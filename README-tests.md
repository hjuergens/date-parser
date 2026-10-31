# Running Tests

## Quick Start

To run all tests for the entire date-parser project:

```bash
./run-tests.sh
```

To run tests for a specific module (e.g., date-rule-combinators):

```bash
./run-tests.sh -m date-rule-combinators
```

Or from within a module directory:

```bash
cd date-rule-combinators
./run-tests.sh
```

## Usage

```bash
./run-tests.sh [options]
```

### Options

| Option | Description |
|--------|-------------|
| `-h, --help` | Show help message |
| `-m, --module MODULE` | Run tests for a specific module |
| `-c, --clean` | Run 'clean' before tests |
| `-o, --offline` | Run Maven in offline mode |
| `-s, --skip` | Skip tests (compile only) |

### Examples

Run all tests:
```bash
./run-tests.sh
```

Run tests for date-rule-combinators:
```bash
./run-tests.sh -m date-rule-combinators
```

Clean and run all tests:
```bash
./run-tests.sh -c
```

Run tests in offline mode:
```bash
./run-tests.sh -o
```

Clean and run tests for a specific module:
```bash
./run-tests.sh -c -m date-rule-antlr
```

## Requirements

- Java 17+ (tested with Azul OpenJDK 18)
- Maven 3.8.6 (automatically downloaded if not present)
- curl or wget (for downloading Maven if needed)

## Environment Variables

You can override the default Java and Maven locations:

```bash
JAVA_HOME=/path/to/java ./run-tests.sh
M2_HOME=/path/to/maven ./run-tests.sh
```

## Notes

- The script automatically downloads Maven 3.8.6 to `/tmp/apache-maven-3.8.6` if not already present
- Java is expected to be available at `/home/juergens/.jdks/azul-18` or can be specified via JAVA_HOME
- Test results are stored in the `target/surefire-reports` directory of each module
