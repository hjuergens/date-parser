/* schedule_parse: the reentrant scanner and the pure parser on one string */
#include <stdio.h>
#include <stdlib.h>
#include "ast.h"
#include "parser.h"
#include "scanner.h"

Schedule *schedule_parse(const char *text, char *error, size_t size) {
    Schedule *schedule = calloc(1, sizeof(Schedule));
    if (!schedule) abort();
    Parse parse = { schedule, error, size };
    error[0] = 0;
    yyscan_t scanner;
    if (yylex_init_extra(&parse, &scanner)) abort();
    YY_BUFFER_STATE buffer = yy_scan_string(text, scanner);
    int status = yyparse(scanner, &parse);
    yy_delete_buffer(buffer, scanner);
    yylex_destroy(scanner);
    if (status) {
        if (!error[0]) snprintf(error, size, "syntax error");
        schedule_free(schedule);
        return NULL;
    }
    return schedule;
}

void schedule_free(Schedule *schedule) {
    if (!schedule) return;
    arena_free(schedule);
    free(schedule);
}
