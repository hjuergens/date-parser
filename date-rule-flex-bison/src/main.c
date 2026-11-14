/* schedule [-b BASE] SCHEDULE: prints the dates of the schedule, one per line, with Monday to Friday as business days
 *
 *   schedule "2015-06-09 + 3M"            2015-09-09
 *   schedule -b 2025-06-09 "T > IMM"      2025-06-18 */
#include <stdio.h>
#include <string.h>
#include "schedule.h"

int main(int argc, char **argv) {
    Context context = { 0, 0, NULL, NULL, NULL };
    int i = 1;
    if (argc == 4 && !strcmp(argv[1], "-b")) {
        if (date_parse_iso(argv[2], &context.base)) {
            fprintf(stderr, "schedule: not a date: %s\n", argv[2]);
            return 2;
        }
        context.has_base = 1;
        i = 3;
    }
    if (argc != i + 1) {
        fprintf(stderr, "usage: schedule [-b BASE] SCHEDULE\n");
        return 2;
    }
    char error[256];
    Schedule *schedule = schedule_parse(argv[i], error, sizeof error);
    DateList dates;
    if (!schedule || schedule_evaluate(schedule, &context, &dates, error, sizeof error)) {
        fprintf(stderr, "schedule: %s\n", error);
        schedule_free(schedule);
        return 1;
    }
    for (int k = 0; k < dates.count; k++) {
        char text[11];
        date_format(dates.dates[k], text);
        puts(text);
    }
    datelist_free(&dates);
    schedule_free(schedule);
    return 0;
}
