package schedule_test

import (
	"fmt"
	"time"

	schedule "github.com/hjuergens/date-parser/date-rule-flex-bison/go"
)

func ExampleParse() {
	rule, err := schedule.Parse("T > NEXT 3RD FRI")
	if err != nil {
		panic(err)
	}
	defer rule.Close()
	base := time.Date(2025, 6, 9, 0, 0, 0, 0, time.UTC)
	dates, err := rule.Evaluate(schedule.Context{Base: &base})
	if err != nil {
		panic(err)
	}
	fmt.Println(dates[0].Format(time.DateOnly))
	// Output: 2025-06-27
}
