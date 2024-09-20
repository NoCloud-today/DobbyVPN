package common

import (
	"io"
	"net"
        "os"
        "log"
	//"logging"
)

var logging = &struct {
	Debug, Info, Warn, Err *log.Logger
}{
	Debug: log.New(io.Discard, "[DEBUG] ", log.LstdFlags),
	Info:  log.New(os.Stdout, "[INFO] ", log.LstdFlags),
	Warn:  log.New(os.Stderr, "[WARN] ", log.LstdFlags),
	Err:   log.New(os.Stderr, "[ERROR] ", log.LstdFlags),
}

func Copy(dst net.Conn, src net.Conn) (written int64, err error) {
	defer func() {
		logging.Info.Printf("Cloak/Copy: Closing source and destination connections")
		src.Close()
		dst.Close()
	}()

	if wt, ok := src.(io.WriterTo); ok {
		logging.Info.Printf("Cloak/Copy: src implements WriteTo, using WriteTo for copy")
		return wt.WriteTo(dst)
	}

	if rt, ok := dst.(io.ReaderFrom); ok {
		logging.Info.Printf("Cloak/Copy: dst implements ReadFrom, using ReadFrom for copy")
		return rt.ReadFrom(src)
	}

	size := 32 * 1024
	buf := make([]byte, size)
	logging.Info.Printf("Cloak/Copy: Buffer of size %d bytes created", size)

	for {
		logging.Info.Printf("Cloak/Copy: Attempting to read from src")
		nr, er := src.Read(buf)
		if nr > 0 {
			logging.Info.Printf("Cloak/Copy: Read %d bytes from src", nr)
			nw, ew := dst.Write(buf[0:nr])
			if nw > 0 {
				written += int64(nw)
				logging.Info.Printf("Cloak/Copy: Wrote %d bytes to dst, total written: %d", nw, written)
			}
			if ew != nil {
				err = ew
				logging.Info.Printf("Cloak/Copy: Error writing to dst: %v", ew)
				break
			}
			if nr != nw {
				err = io.ErrShortWrite
				logging.Info.Printf("Cloak/Copy: Short write, expected %d bytes, but wrote %d bytes", nr, nw)
				break
			}
		}
		if er != nil {
			if er != io.EOF {
				err = er
				logging.Info.Printf("Cloak/Copy: Error reading from src: %v", er)
			} else {
				logging.Info.Printf("Cloak/Copy: End of file (EOF) reached")
			}
			break
		}
	}
	logging.Info.Printf("Cloak/Copy: Copy operation completed, total bytes written: %d, error: %v", written, err)
	return written, err
}