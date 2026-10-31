# run-tests.sh - Test Runner Script

This script automates running tests for the date-parser project and its modules.

## Features

- Automatically detects and uses Java from your system or default location
- Downloads Maven 3.8.6 if not already installed
- Works with all date-parser modules
- Handles the `.mvn` directory settings issue automatically
- Clean, colorized output

## Quick Start

From the project root:

```bash
# Run all tests
./run-tests.sh

# Run tests for a specific module
./run-tests.sh -m date-rule-combinators

# Clean and run tests
./run-tests.sh -c
```

## Options

| Option | Description |
|--------|-------------|
| `-h, --help` | Show help message and exit |
| `-m, --module MODULE` | Run tests for a specific module only |
| `-c, --clean` | Run `mvn clean` before tests |
| `-o, --offline` | Run Maven in offline mode |
| `-s, --skip` | Skip tests (compile only) |

## Examples

### Run all tests
```bash
./run-tests.sh
```

### Run tests for a specific module
```bash
./run-tests.sh -m date-rule-combinators
./run-tests.sh --module date-rule-antlr
```

### Clean and test
```bash
./run-tests.sh -c
./run-tests.sh --clean
```

### Clean and test a specific module
```bash
./run-tests.sh -c -m date-rule-combinators
```

### Run in offline mode
```bash
./run-tests.sh -o
```

### Just compile (skip tests)
```bash
./run-tests.sh -s
```

## Module-specific Scripts

Each module has its own `run-tests.sh` script that wraps the project-level script:

```bash
cd date-rule-combinators
./run-tests.sh  # Runs tests for this module only
```

## Environment Variables

You can override the default Java and Maven locations:

```bash
# Use a custom Java installation
JAVA_HOME=/path/to/java ./run-tests.sh

# Use a custom Maven installation
M2_HOME=/path/to/maven ./run-tests.sh

# Both
JAVA_HOME=/path/to/java M2_HOME=/path/to/maven ./run-tests.sh
```

## Default Locations

- **Java**: `/home/juergens/.jdks/azul-18` (Azul OpenJDK 18)
- **Maven**: `/tmp/apache-maven-3.8.6` (automatically downloaded if missing)

## Requirements

- Java 17+ (the project uses Java 17)
- Either `curl` or `wget` for downloading Maven (if not already installed)

## Notes

- The script temporarily renames the `.mvn` directory to avoid Maven settings path resolution issues, then restores it after execution.
- Test results are stored in the `target/surefire-reports` directory of each module.
- The script exits with code 0 if all tests pass, or non-zero if any test fails.

## Troubleshooting

### Java not found
```
[ERROR] Java not found. Please set JAVA_HOME or install Java 17+
```

Solution: Install Java 17+ or set the `JAVA_HOME` environment variable.

### Maven not found
The script will automatically download Maven 3.8.6 to `/tmp/apache-maven-3.8.6`.

If you want to use a different version, set `M2_HOME` before running:
```bash
M2_HOME=/path/to/your/maven ./run-tests.sh
```

### Network issues
If you have network restrictions, download Maven manually and set `M2_HOME`:
```bash
M2_HOME=/path/to/maven ./run-tests.sh
```
