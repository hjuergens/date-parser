/* SWIG interface of the C schedule library (../../../src/schedule.h) for Go. The go command runs SWIG on this file
 * itself (swig -go -cgo -intgosize 64), so the generated wrapper is never committed.
 *
 * Two functions of schedule.h write their message into a caller's buffer (char *error, size_t size), which SWIG can't
 * map to Go; they are replaced by helpers that take an ErrorBuffer, a struct whose message SWIG reads as a Go string.
 * There is no thread-local error state, as consecutive cgo calls may run on different OS threads. */
%module cschedule

%{
#include "schedule.h"
%}

/* the opaque types of schedule.h, declared so that SWIG gives them Go types; C creates and frees them */
%nodefaultctor Schedule;
%nodefaultdtor Schedule;
%nodefaultctor Market;
%nodefaultdtor Market;
%nodefaultctor Events;
%nodefaultdtor Events;
struct Schedule {};
struct Market {};
struct Events {};

%ignore schedule_parse;
%ignore schedule_evaluate;
%ignore date_parse_iso;
%ignore date_format;
%include "../../../src/schedule.h"

%inline %{
typedef struct {
    char message[256];
} ErrorBuffer;

/* NULL on a syntax error, with the message in error */
Schedule *parse_rule(const char *text, ErrorBuffer *error) {
    return schedule_parse(text, error->message, sizeof error->message);
}

/* 0 and the dates in out, or -1 and the message in error */
int evaluate_rule(const Schedule *schedule, const Context *context, DateList *out, ErrorBuffer *error) {
    return schedule_evaluate(schedule, context, out, error->message, sizeof error->message);
}

int datelist_at(const DateList *list, int i) {
    return list->dates[i];
}
%}
