# make test: generate scanner and parser, build, run the shared cases. Needs gcc, flex and bison (see Dockerfile).
# make test-go: the same cases from Go through the SWIG binding in go/; needs Go and SWIG as well.
CC ?= cc
CFLAGS ?= -O2
WARNINGS = -std=c11 -D_DEFAULT_SOURCE -Wall -Wextra -Werror
EXAMPLES = ../examples/schedule.txt ../examples/catalog.txt ../examples/date-holidays.txt

LIBRARY = build/parser.o build/scanner.o build/parse.o build/semantics.o

all: build/schedule build/run_cases build/libschedule.a

build/parser.c build/parser.h: src/parser.y
	@mkdir -p build
	bison -Werror=conflicts-sr -Werror=conflicts-rr --defines=build/parser.h -o build/parser.c $<

build/scanner.c build/scanner.h: src/scanner.l
	@mkdir -p build
	flex --header-file=build/scanner.h -o build/scanner.c $<

# generated code: the warnings of flex's skeleton are not ours
build/parser.o: build/parser.c src/ast.h
	$(CC) $(CFLAGS) -std=c11 -D_DEFAULT_SOURCE -Isrc -Ibuild -c -o $@ $<
build/scanner.o: build/scanner.c build/parser.h src/ast.h
	$(CC) $(CFLAGS) -std=c11 -D_DEFAULT_SOURCE -Isrc -Ibuild -c -o $@ $<

build/parse.o: src/parse.c build/parser.h build/scanner.h src/ast.h
	$(CC) $(CFLAGS) $(WARNINGS) -Isrc -Ibuild -c -o $@ $<
build/semantics.o: src/semantics.c src/ast.h src/lunar_tables.h
	$(CC) $(CFLAGS) $(WARNINGS) -Isrc -c -o $@ $<

build/schedule: src/main.c $(LIBRARY)
	$(CC) $(CFLAGS) $(WARNINGS) -Isrc -o $@ $^ -lm
build/run_cases: test/run_cases.c test/spec.c $(LIBRARY)
	$(CC) $(CFLAGS) $(WARNINGS) -Isrc -Itest -o $@ $^ -lm

# the library the Go binding links
build/libschedule.a: $(LIBRARY)
	ar rcs $@ $^

test: build/run_cases
	build/run_cases $(EXAMPLES)

test-go: build/libschedule.a
	cd go && go vet -unsafeptr=false ./... && go test ./...   # SWIG's wrapper converts uintptr to unsafe.Pointer

clean:
	rm -rf build

.PHONY: all test test-go clean
