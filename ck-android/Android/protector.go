//go:build !android
// +build !android

package cloak_outline

import "syscall"

func protector(string, string, syscall.RawConn) error {
	return nil
}
