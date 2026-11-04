// Command bench measures the C schedule library called from Go through the SWIG binding, with the protocol of
// benchmark/schedule/README.md in the project root (shared with bench_c and bench_antlr.py).
//
//	bench ROUNDS FILE...   prints one JSON object
//	bench 0                start-up only: parses and evaluates one schedule and prints its date
package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"time"

	schedule "github.com/hjuergens/date-parser/date-rule-flex-bison/go"
	"github.com/hjuergens/date-parser/date-rule-flex-bison/go/internal/spec"
)

type checksum struct {
	ParseErrors int   `json:"parse_errors"`
	Dates       int64 `json:"dates"`
	Sum         int64 `json:"sum"`
	Errors      int64 `json:"errors"`
}

// parseRound parses every input of the loaded specs
func parseRound(specs []*spec.Spec) int {
	errors := 0
	for _, s := range specs {
		for _, c := range s.Cases {
			rule, err := schedule.Parse(c.Input)
			if err != nil {
				errors++
				continue
			}
			rule.Close()
		}
	}
	return errors
}

// evaluateRound reads the files (fresh markets and events), then parses and evaluates every case
func evaluateRound(files []string) checksum {
	var sum checksum
	for _, file := range files {
		s, err := spec.Load(file)
		if err != nil {
			panic(err)
		}
		for _, c := range s.Cases {
			if c.MissingMarket != "" {
				sum.Errors++
				continue
			}
			rule, err := schedule.Parse(c.Input)
			if err != nil {
				sum.Errors++
				continue
			}
			dates, err := rule.Evaluate(c.Context)
			rule.Close()
			if err != nil {
				sum.Errors++
				continue
			}
			sum.Dates += int64(len(dates))
			for _, d := range dates {
				sum.Sum += d.Unix() / 86400
			}
		}
		s.Close()
	}
	return sum
}

func main() {
	if len(os.Args) == 2 && os.Args[1] == "0" {
		rule, err := schedule.Parse("2015-06-09 + 3M")
		if err != nil {
			panic(err)
		}
		dates, err := rule.Evaluate(schedule.Context{})
		if err != nil {
			panic(err)
		}
		fmt.Println(dates[0].Format(time.DateOnly))
		return
	}
	if len(os.Args) < 3 {
		fmt.Fprintln(os.Stderr, "usage: bench ROUNDS FILE...")
		os.Exit(2)
	}
	rounds, _ := strconv.Atoi(os.Args[1])
	files := os.Args[2:]
	var specs []*spec.Spec
	cases := 0
	for _, file := range files {
		s, err := spec.Load(file)
		if err != nil {
			panic(err)
		}
		specs = append(specs, s)
		cases += len(s.Cases)
	}
	measure := func(round func()) []float64 {
		times := make([]float64, rounds)
		for r := range times {
			start := time.Now()
			round()
			times[r] = time.Since(start).Seconds()
		}
		return times
	}
	parseErrors := parseRound(specs) // warm-up
	parse := measure(func() { parseRound(specs) })
	sum := evaluateRound(files) // warm-up
	evaluate := measure(func() { evaluateRound(files) })
	sum.ParseErrors = parseErrors
	out, _ := json.Marshal(map[string]any{"implementation": "go-swig", "cases": cases,
		"parse": parse, "evaluate": evaluate, "checksum": sum})
	fmt.Println(string(out))
	for _, s := range specs {
		s.Close()
	}
}
