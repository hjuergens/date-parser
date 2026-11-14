// Package cschedule is the SWIG-generated binding of the C schedule library; package schedule wraps it for Go.
//
// The library is linked from build/libschedule.a of the module, built by "make build/libschedule.a".
package cschedule

// #cgo CFLAGS: -I${SRCDIR}/../../../src
// #cgo LDFLAGS: -L${SRCDIR}/../../../build -lschedule -lm
import "C"
