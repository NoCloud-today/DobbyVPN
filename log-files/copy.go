/*
Copyright (c) 2009 The Go Authors. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
Forked from https://golang.org/src/io/io.go
*/
package common

import (
	"io"
	"net"
        "log"
)

func Copy(dst net.Conn, src net.Conn) (written int64, err error) {
	defer func() {
		log.Printf("Cloak/Copy: Closing source and destination connections")
		src.Close()
		dst.Close()
	}()

	// If the reader has a WriteTo method, use it to do the copy.
	// Avoids an allocation and a copy.
	if wt, ok := src.(io.WriterTo); ok {
		log.Printf("Cloak/Copy: src implements WriteTo, using WriteTo for copy")
		return wt.WriteTo(dst)
	}

	// Similarly, if the writer has a ReadFrom method, use it to do the copy.
	if rt, ok := dst.(io.ReaderFrom); ok {
		log.Printf("Cloak/Copy: dst implements ReadFrom, using ReadFrom for copy")
		return rt.ReadFrom(src)
	}

	size := 32 * 1024
	buf := make([]byte, size)
	log.Printf("Cloak/Copy: Buffer of size %d bytes created", size)

	for {
		log.Printf("Cloak/Copy: Attempting to read from src")
		nr, er := src.Read(buf)
		if nr > 0 {
			log.Printf("Cloak/Copy: Read %d bytes from src", nr)
			nw, ew := dst.Write(buf[0:nr])
			if nw > 0 {
				written += int64(nw)
				log.Printf("Cloak/Copy: Wrote %d bytes to dst, total written: %d", nw, written)
			}
			if ew != nil {
				err = ew
				log.Printf("Cloak/Copy: Error writing to dst: %v", ew)
				break
			}
			if nr != nw {
				err = io.ErrShortWrite
				log.Printf("Cloak/Copy: Short write, expected %d bytes, but wrote %d bytes", nr, nw)
				break
			}
		}
		if er != nil {
			if er != io.EOF {
				err = er
				log.Printf("Cloak/Copy: Error reading from src: %v", er)
			} else {
				log.Printf("Cloak/Copy: End of file (EOF) reached")
			}
			break
		}
	}
	log.Printf("Cloak/Copy: Copy operation completed, total bytes written: %d, error: %v", written, err)
	return written, err
}