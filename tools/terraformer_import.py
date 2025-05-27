#!/usr/bin/env python3

import argparse
import json
import os
import subprocess
import sys
import logging
import shutil
from pathlib import Path
import re
import platform

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger("terraformer-import")

# ANSI color codes for prettier output
BLUE = "\033[0;34m"
GREEN = "\033[0;32m"
YELLOW = "\033[0;33m"
RED = "\033[0;31m"
NC = "\033[0m"  # No Color

def print_colored(text, color):
    """Print text with color"""
    print(f"{color}{text}{NC}")

def run_command(cmd, capture_output=False, cwd=None):
    """Run a shell command and handle errors"""
    try:
        logger.info(f"Running command: {' '.join(cmd)}")
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=False,
            cwd=cwd
        )

        if result.stdout and not capture_output:
            print(result.stdout)

        if result.stderr and not capture_output:
            print_colored(result.stderr, YELLOW)

        if result.returncode != 0:
            logger.error(f"Command failed with exit code {result.returncode}")
            logger.error(f"Error: {result.stderr}")
            return False, result.stdout, result.stderr

        return True, result.stdout, result.stderr
    except Exception as e:
        logger.error(f"Error running command {' '.join(cmd)}: {e}")
        return False, "", str(e)

def install_terraformer():
    """Install Terraformer"""
    print_colored("Installing Terraformer...", BLUE)

    # Determine OS and architecture
    system = platform.system().lower()
    arch = platform.machine()

    # Map architecture to terraformer naming
    arch_map = {
        'x86_64': 'amd64',
        'amd64': 'amd64',
        'arm64': 'arm64',
        'aarch64': 'arm64'
    }

    if arch in arch_map:
        arch = arch_map[arch]
    else:
        print_colored(f"Unsupported architecture: {arch}", RED)
        return False

    # Set Terraformer version
    version = "0.8.24"

    # Create temporary directory
    os.makedirs("tmp", exist_ok=True)

    if system == 'linux':
        binary_name = f"terraformer-aws-linux-{arch}"
    elif system == 'darwin':
        binary_name = f"terraformer-aws-darwin-{arch}"
    else:
        print_colored(f"Unsupported system: {system}", RED)
        return False

    # Download URL
    url = f"https://github.com/GoogleCloudPlatform/terraformer/releases/download/{version}/{binary_name}"

    # Download Terraformer
    print_colored(f"Downloading Terraformer from {url}", BLUE)
    success, _, _ = run_command(["curl", "-L", url, "-o", "tmp/terraformer"])
    if not success:
        return False

    # Make executable
    success, _, _ = run_command(["chmod", "+x", "tmp/terraformer"])
    if not success:
        return False

    # Move to /usr/local/bin if running as root, otherwise to ~/.local/bin
    if os.geteuid() == 0:
        dest_dir = "/usr/local/bin"
    else:
        dest_dir = os.path.expanduser("~/.local/bin")
        os.makedirs(dest_dir, exist_ok=True)

        # Add to PATH if not already there
        if dest_dir not in os.environ["PATH"]:
            print_colored(f"Adding {dest_dir} to PATH for this session", YELLOW)
            os.environ["PATH"] += f":{dest_dir}"

    # Move terraformer to bin directory
    try:
        shutil.move("tmp/terraformer", f"{dest_dir}/terraformer")
        print_colored(f"Terraformer installed to {dest_dir}/terraformer", GREEN)
    except Exception as e:
        print_colored(f"Error moving terraformer: {e}", RED)
        return False

    # Verify installation
    success, stdout, _ = run_command(["terraformer", "--version"], capture_output=True)
    if success:
        print_colored(f"Terraformer installed successfully: {stdout.strip()}", GREEN)
        return True
    else:
        print_colored("Failed to install Terraformer", RED)
        return False

def prepare_terraform_environment():
    """Prepare Terraform environment by creating necessary directories"""
    # Create ~/.terraform.d/plugins directory if it doesn't exist
    terraform_plugin_dir = os.path.expanduser("~/.terraform.d/plugins")

    # For Linux, create architecture-specific directory
    if sys.platform.startswith('linux'):
        terraform_plugin_dir = os.path.join(terraform_plugin_dir, "linux_amd64")
    elif sys.platform == 'darwin':
        terraform_plugin_dir = os.path.join(terraform_plugin_dir, "darwin_amd64")

    os.makedirs(terraform_plugin_dir, exist_ok=True)
    logger.info(f"Created Terraform plugin directory: {terraform_plugin_dir}")

    # Make sure terraform is initialized
    return True

def check_terraformer_installed():
    """Check if Terraformer is installed"""
    success, stdout, _ = run_command(["terraformer", "version"], capture_output=True)
    if not success:
        print_colored("Terraformer is not installed or not in PATH", RED)
        return False

    print_colored(f"Terraformer version: {stdout.strip()}", GREEN)

    # Verify it's working by running a simple command
    success, _, _ = run_command(["terraformer", "import", "aws", "--list"], capture_output=True)
    if not success:
        print_colored("Terraformer is installed but not working correctly", RED)
        return False

    return True

def verify_aws_credentials():
    """Verify AWS credentials are set"""
    required_vars = ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION"]
    missing_vars = [var for var in required_vars if var not in os.environ or not os.environ[var]]

    if missing_vars:
        print_colored(f"Missing AWS environment variables: {', '.join(missing_vars)}", RED)
        print_colored("Please set the following environment variables:", YELLOW)
        for var in missing_vars:
            print_colored(f"  export {var}=your_{var.lower()}", YELLOW)
        return False

    # Verify credentials work by making a simple AWS CLI call
    print_colored("Verifying AWS credentials...", BLUE)
    success, stdout, _ = run_command(["aws", "sts", "get-caller-identity", "--output", "json"], capture_output=True)

    if success:
        try:
            identity = json.loads(stdout)
            print_colored(f"AWS credentials verified. Using account: {identity['Account']}", GREEN)
            return True
        except json.JSONDecodeError:
            print_colored("Failed to parse AWS identity response", RED)
            return False
    else:
        print_colored("Failed to verify AWS credentials", RED)
        return False

def run_terraformer(resources, output_dir, region=None, filters=None, profile=None):
    """Run Terraformer to import AWS resources"""
    print_colored(f"Importing AWS resources with Terraformer: {resources}", BLUE)

    # Ensure the output directory exists
    os.makedirs(output_dir, exist_ok=True)

    # Prepare Terraform environment
    prepare_terraform_environment()

    # Build the command
    cmd = ["terraformer", "import", "aws", f"--resources={resources}"]

    # Add region if specified
    if region:
        cmd.append(f"--regions={region}")
    elif "AWS_REGION" in os.environ:
        cmd.append(f"--regions={os.environ['AWS_REGION']}")

    # Add filters if specified
    if filters:
        for filter_str in filters:
            cmd.append(f"--filter={filter_str}")

    # Add AWS profile if specified
    if profile:
        cmd.append(f"--profile={profile}")

    # Add output directory and path pattern
    cmd.extend([
        "--path-pattern={output}/{provider}/{service}",
        "--compact",
        f"--output={output_dir}"
    ])

    # Run Terraformer
    success, _, _ = run_command(cmd)

    if success:
        print_colored(f"Successfully imported resources to {output_dir}", GREEN)
    else:
        print_colored("Failed to import resources with Terraformer", RED)

    return success

def list_imported_resources(output_dir):
    """List all imported Terraform files"""
    print_colored(f"Listing imported resources in {output_dir}:", BLUE)

    tf_files = list(Path(output_dir).glob("**/*.tf"))
    if not tf_files:
        print_colored("No Terraform files were imported", YELLOW)
        return

    for tf_file in sorted(tf_files):
        print(f"- {tf_file}")

    print_colored(f"Total: {len(tf_files)} Terraform files", GREEN)

def process_imported_resources(output_dir, terraform_dir):
    """Process imported resources to integrate with existing Terraform code"""
    print_colored("Processing imported resources...", BLUE)

    # Create terraform directory for imported resources
    imported_dir = os.path.join(terraform_dir, "imported")
    os.makedirs(imported_dir, exist_ok=True)

    # Track if any files were copied
    files_copied = False

    # Move all .tf files to the imported directory, preserving directory structure
    for root, _, files in os.walk(output_dir):
        for file in files:
            if file.endswith('.tf'):
                # Get relative path from output_dir
                rel_path = os.path.relpath(root, output_dir)

                # Create target directory
                target_dir = os.path.join(imported_dir, rel_path)
                os.makedirs(target_dir, exist_ok=True)

                # Copy file
                src_file = os.path.join(root, file)
                dst_file = os.path.join(target_dir, file)
                shutil.copy2(src_file, dst_file)
                files_copied = True
                print(f"Copied {src_file} to {dst_file}")

    if not files_copied:
        print_colored("No resources were imported. Check your filter criteria.", YELLOW)
        return

    # Generate import.tf file with import statements
    with open(os.path.join(terraform_dir, "import.tf"), "w") as import_file:
        import_file.write("# Import statements for Terraformer-imported resources\n")
        import_file.write("# Run: terraform init && terraform plan to verify imports\n\n")

        # Scan all .tfstate files for resource references
        for tfstate_file in Path(output_dir).glob("**/*.tfstate"):
            try:
                with open(tfstate_file, "r") as f:
                    tfstate = json.load(f)
                    for resource in tfstate.get("resources", []):
                        resource_type = resource.get("type")
                        resource_name = resource.get("name")

                        for instance in resource.get("instances", []):
                            resource_id = instance.get("attributes", {}).get("id")
                            if resource_type and resource_name and resource_id:
                                import_line = f'terraform import {resource_type}.{resource_name} "{resource_id}"\n'
                                import_file.write(import_line)
            except Exception as e:
                logger.warning(f"Failed to process {tfstate_file}: {e}")

    print_colored(f"Generated import.tf with import statements in {terraform_dir}", GREEN)
    print_colored(f"Imported resources are in {imported_dir}", GREEN)
    print_colored("Next steps:", BLUE)
    print_colored("1. Review the imported resources in the 'imported' directory", BLUE)
    print_colored("2. Run 'terraform init && terraform plan' to verify the imports", BLUE)
    print_colored("3. Modify the imported resources as needed to fit your Terraform code", BLUE)

def initialize_terraform(terraform_dir):
    """Initialize Terraform in the specified directory"""
    print_colored(f"Initializing Terraform in {terraform_dir}...", BLUE)

    # Ensure the directory exists
    os.makedirs(terraform_dir, exist_ok=True)

    # If terraform_dir has a main.tf file, initialize terraform
    if os.path.exists(os.path.join(terraform_dir, "main.tf")):
        # Run terraform init
        success, _, _ = run_command(["terraform", "init"], cwd=terraform_dir)
        if success:
            print_colored("Terraform initialized successfully", GREEN)
        else:
            print_colored("Failed to initialize Terraform", RED)
        return success
    else:
        print_colored(f"No main.tf found in {terraform_dir}, skipping terraform init", YELLOW)
        return True

def main():
    """Main function"""
    parser = argparse.ArgumentParser(description="Import AWS resources with Terraformer")
    parser.add_argument("--resources", required=True, help="AWS resources to import (comma-separated)")
    parser.add_argument("--output-dir", default="terraformer-output", help="Output directory for Terraformer")
    parser.add_argument("--terraform-dir", default="terraform", help="Terraform directory to copy resources to")
    parser.add_argument("--region", help="AWS region (defaults to AWS_REGION env var)")
    parser.add_argument("--profile", help="AWS profile to use")
    parser.add_argument("--filters", nargs="+", help="Filters for resources (e.g. 'Name=tag:Environment;Value=staging')")
    parser.add_argument("--install", action="store_true", help="Install Terraformer if not already installed")

    args = parser.parse_args()

    # Check if Terraformer is installed
    if not check_terraformer_installed():
        if args.install:
            if not install_terraformer():
                sys.exit(1)
        else:
            print_colored("Terraformer is not installed. Use --install to install it.", RED)
            sys.exit(1)

    # Initialize Terraform
    if not initialize_terraform(args.terraform_dir):
        sys.exit(1)

    # Verify AWS credentials
    if not verify_aws_credentials():
        sys.exit(1)

    # Run Terraformer
    if not run_terraformer(
        args.resources,
        args.output_dir,
        args.region,
        args.filters,
        args.profile
    ):
        sys.exit(1)

    # List imported resources
    list_imported_resources(args.output_dir)

    # Process imported resources
    process_imported_resources(args.output_dir, args.terraform_dir)

    print_colored("Import completed successfully!", GREEN)

if __name__ == "__main__":
    main()