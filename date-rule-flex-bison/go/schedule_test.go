package schedule_test

import (
	"fmt"
	"path/filepath"
	"strings"
	"testing"
	"time"

	schedule "github.com/hjuergens/date-parser/date-rule-flex-bison/go"
	"github.com/hjuergens/date-parser/date-rule-flex-bison/go/internal/spec"
)

// TestSpec runs the shared cases of the three spec files through the SWIG binding, one subtest per case.
func TestSpec(t *testing.T) {
	for _, file := range []string{"schedule.txt", "catalog.txt", "date-holidays.txt"} {
		s, err := spec.Load(filepath.Join("..", "..", "examples", file))
		if err != nil {
			t.Fatal(err)
		}
		for _, broken := range s.Broken {
			t.Error(broken)
		}
		for _, c := range s.Cases {
			t.Run(fmt.Sprintf("%s:%d", file, c.Line), func(t *testing.T) {
				if c.MissingMarket != "" {
					t.Fatalf("no market %s", c.MissingMarket)
				}
				got, err := evaluate(c)
				if c.Expected == "error" {
					if err == nil {
						t.Errorf("%s\n got %s, want an error", c.Text, format(got))
					}
				} else if err != nil {
					t.Errorf("%s\n got error %v", c.Text, err)
				} else if format(got) != normalize(c.Expected) {
					t.Errorf("%s\n got %s", c.Text, format(got))
				}
			})
		}
		s.Close()
	}
}

func evaluate(c spec.Case) ([]time.Time, error) {
	rule, err := schedule.Parse(c.Input)
	if err != nil {
		return nil, err
	}
	defer rule.Close()
	return rule.Evaluate(c.Context)
}

func format(dates []time.Time) string {
	if len(dates) == 0 {
		return "none"
	}
	texts := make([]string, len(dates))
	for i, d := range dates {
		texts[i] = d.Format(time.DateOnly)
	}
	return strings.Join(texts, ", ")
}

func normalize(expected string) string {
	items := strings.Split(expected, ",")
	for i := range items {
		items[i] = strings.TrimSpace(items[i])
	}
	return strings.Join(items, ", ")
}

func TestOwnership(t *testing.T) {
	rule, err := schedule.Parse("25 DEC")
	if err != nil {
		t.Fatal(err)
	}
	market := schedule.NewMarket(rule)
	defer market.Close()
	rule.Close() // handed over: a no-op, not a double free
	if _, err := rule.Evaluate(schedule.Context{}); err == nil {
		t.Error("a rule handed over to a market still evaluates")
	}
	spot, _ := schedule.Parse("T > SPOT")
	defer spot.Close()
	base := time.Date(2015, 12, 23, 0, 0, 0, 0, time.UTC)
	got, err := spot.Evaluate(schedule.Context{Base: &base, Market: market})
	if err != nil || format(got) != "2015-12-28" {
		t.Errorf("T > SPOT on 2015-12-23 with 25 DEC as holiday: got %v %v", got, err)
	}
}

func TestSyntaxError(t *testing.T) {
	if _, err := schedule.Parse("2015-06-09 > SPOT EURUSDX"); err == nil || !strings.Contains(err.Error(), "syntax error") {
		t.Errorf("got %v", err)
	}
}
