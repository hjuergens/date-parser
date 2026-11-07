// Package spec reads the shared spec files of date-parser (examples/schedule.txt, catalog.txt, date-holidays.txt):
//
//	<input> => <expected>      [<base>] <input> => ...      [<base> <market>] <input> => ...
//	market <name> = <schedule>  event <name> = <schedule>
//
// like test/spec.c of the C module. The declarations are read first, so the events are known to every case of the
// file; each Spec has its own markets, whose holiday caches fill as the cases are evaluated.
package spec

import (
	"fmt"
	"os"
	"regexp"
	"strings"
	"time"

	schedule "github.com/hjuergens/date-parser/date-rule-flex-bison/go"
)

// Case is one line "<input> => <expected>".
type Case struct {
	Line          int
	Text          string // the line as it is in the file
	Input         string // without the prefix
	Expected      string // a comma-separated list of ISO dates, "none" or "error"
	Context       schedule.Context
	MissingMarket string // the prefix names a market that isn't declared
}

// Spec is a read spec file.
type Spec struct {
	Cases   []Case
	Broken  []string // declarations that don't parse
	markets map[string]*schedule.Market
	events  *schedule.Events
}

var prefix = regexp.MustCompile(`^\[(\d{4}-\d{2}-\d{2})(?: ([A-Z0-9_]+))?(?: ([A-Z0-9_]+))?\] (.*)$`) // [base], [base market] or [base market fixing]

// Load reads a spec file; close it when done.
func Load(path string) (*Spec, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	lines := strings.Split(string(content), "\n")
	s := &Spec{markets: map[string]*schedule.Market{}, events: schedule.NewEvents()}
	for i, line := range lines {
		isMarket, isEvent := strings.HasPrefix(line, "market "), strings.HasPrefix(line, "event ")
		if !isMarket && !isEvent {
			continue
		}
		name, text, found := strings.Cut(strings.TrimPrefix(strings.TrimPrefix(line, "market "), "event "), "=")
		if !found {
			continue
		}
		name = strings.TrimSpace(name)
		rule, err := schedule.Parse(strings.TrimSpace(text))
		if err != nil {
			s.Broken = append(s.Broken, fmt.Sprintf("%s:%d: %s: %v", path, i+1, name, err))
		} else if isMarket {
			s.markets[name] = schedule.NewMarket(rule)
		} else {
			s.events.Add(name, rule)
		}
	}
	for i, line := range lines {
		if strings.TrimSpace(line) == "" || strings.HasPrefix(line, "#") ||
			strings.HasPrefix(line, "market ") || strings.HasPrefix(line, "event ") {
			continue
		}
		arrow := strings.LastIndex(line, "=>")
		if arrow < 0 {
			continue
		}
		c := Case{Line: i + 1, Text: strings.TrimSpace(line), Input: strings.TrimSpace(line[:arrow]),
			Expected: strings.TrimSpace(line[arrow+2:]), Context: schedule.Context{Events: s.events}}
		if m := prefix.FindStringSubmatch(c.Input); m != nil {
			base, err := time.Parse(time.DateOnly, m[1])
			if err != nil {
				return nil, fmt.Errorf("%s:%d: %v", path, i+1, err)
			}
			c.Context.Base, c.Input = &base, m[4]
			if m[2] != "" {
				if c.Context.Market = s.markets[m[2]]; c.Context.Market == nil {
					c.MissingMarket = m[2]
				}
			}
			if m[3] != "" {
				if c.Context.Fixing = s.markets[m[3]]; c.Context.Fixing == nil {
					c.MissingMarket = m[3]
				}
			}
		}
		s.Cases = append(s.Cases, c)
	}
	return s, nil
}

// Close frees the markets and events.
func (s *Spec) Close() {
	for _, m := range s.markets {
		m.Close()
	}
	s.events.Close()
}
