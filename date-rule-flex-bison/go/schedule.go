// Package schedule evaluates the shared "schedule" language of date-parser with the C library of date-rule-flex-bison
// (a Flex scanner, a Bison parser and a C evaluator), called through a binding that SWIG generates.
//
// A parsed schedule is a rule: evaluate it with a Context (base date, market and named events).
//
//	rule, err := schedule.Parse("T > NEXT 3RD FRI")
//	defer rule.Close()
//	base := time.Date(2025, 6, 9, 0, 0, 0, 0, time.UTC)
//	dates, err := rule.Evaluate(schedule.Context{Base: &base})   // [2025-06-27]
//
// Schedules, markets and events hold C memory: close them when done. A Market caches the holidays of the years it
// has been asked about, so neither it nor a Context using it may be shared between goroutines.
package schedule

import (
	"errors"
	"time"

	"github.com/hjuergens/date-parser/date-rule-flex-bison/go/internal/cschedule"
)

// Schedule is a parsed rule.
type Schedule struct {
	c cschedule.Schedule // nil once closed or handed over to a market or events
}

// Market says which days are business days: Monday to Friday except the dates of its rule, evaluated with the first
// of January of a year as base date.
type Market struct {
	c cschedule.Market
}

// Events are named rules, referred to as @NAME.
type Events struct {
	c cschedule.Events
}

// Context is what a rule is evaluated against. A nil Base is fine unless the rule uses T; a nil Market or Fixing
// (the fixing calendar of F, FD and FIX) means Monday to Friday.
type Context struct {
	Base   *time.Time
	Market *Market
	Events *Events
	Fixing *Market
}

// Parse returns the rule of a schedule, or the syntax error.
func Parse(text string) (*Schedule, error) {
	buffer := cschedule.NewErrorBuffer()
	defer cschedule.DeleteErrorBuffer(buffer)
	rule := cschedule.Parse_rule(text, buffer)
	if rule == nil || rule.Swigcptr() == 0 {
		return nil, errors.New(buffer.GetMessage())
	}
	return &Schedule{rule}, nil
}

// Close frees the rule. It is a no-op after the rule was closed or handed over.
func (s *Schedule) Close() {
	if s != nil && s.c != nil {
		cschedule.Schedule_free(s.c)
		s.c = nil
	}
}

// take hands the C rule over to a new owner
func (s *Schedule) take() cschedule.Schedule {
	if s == nil || s.c == nil {
		panic("schedule: rule is closed or already handed over")
	}
	c := s.c
	s.c = nil
	return c
}

// Evaluate returns the dates of the rule, as midnight UTC.
func (s *Schedule) Evaluate(context Context) ([]time.Time, error) {
	if s == nil || s.c == nil {
		return nil, errors.New("schedule: rule is closed")
	}
	c := cschedule.NewContext()
	defer cschedule.DeleteContext(c)
	if context.Base != nil {
		b := context.Base
		c.SetHas_base(1)
		c.SetBase(cschedule.Date_of(b.Year(), int(b.Month()), b.Day()))
	}
	if context.Market != nil {
		c.SetMarket(context.Market.c)
	}
	if context.Fixing != nil {
		c.SetFixing(context.Fixing.c)
	}
	if context.Events != nil {
		c.SetEvents(context.Events.c)
	}
	buffer := cschedule.NewErrorBuffer()
	defer cschedule.DeleteErrorBuffer(buffer)
	list := cschedule.NewDateList()
	defer cschedule.DeleteDateList(list)
	if cschedule.Evaluate_rule(s.c, c, list, buffer) != 0 {
		return nil, errors.New(buffer.GetMessage())
	}
	defer cschedule.Datelist_free(list)
	dates := make([]time.Time, list.GetCount())
	for i := range dates {
		dates[i] = Day(cschedule.Datelist_at(list, i))
	}
	return dates, nil
}

// Day is the date of a day number of the C library: days since 1970-01-01, as midnight UTC.
func Day(days int) time.Time {
	return time.Unix(int64(days)*86400, 0).UTC()
}

// NewMarket returns the market of a holiday rule; the market takes the rule over, so don't close it.
func NewMarket(rule *Schedule) *Market {
	return &Market{cschedule.Market_new(rule.take())}
}

// Close frees the market and its rule.
func (m *Market) Close() {
	if m != nil && m.c != nil {
		cschedule.Market_free(m.c)
		m.c = nil
	}
}

// NewEvents returns an empty set of events.
func NewEvents() *Events {
	return &Events{cschedule.Events_new()}
}

// Add declares the event @name; the events take the rule over, so don't close it.
func (e *Events) Add(name string, rule *Schedule) {
	cschedule.Events_add(e.c, name, rule.take())
}

// Close frees the events and their rules.
func (e *Events) Close() {
	if e != nil && e.c != nil {
		cschedule.Events_free(e.c)
		e.c = nil
	}
}
