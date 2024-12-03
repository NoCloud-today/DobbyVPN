package main

import (
	"C"
	"context"
	"io"
	"log"
	"os"
)

var logging = &struct {
	Debug, Info, Warn, Err *log.Logger
}{
	Debug: log.New(io.Discard, "[DEBUG] ", log.LstdFlags),
	Info:  log.New(os.Stdout, "[INFO] ", log.LstdFlags),
	Warn:  log.New(os.Stderr, "[WARN] ", log.LstdFlags),
	Err:   log.New(os.Stderr, "[ERROR] ", log.LstdFlags),
}

var cancelFunc context.CancelFunc

// Инициализация логирования в файл
func initLogToFile() (*os.File, error) {
	file, err := os.OpenFile("logs.txt", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		return nil, err
	}
	return file, nil
}

// Перенаправление стандартного вывода и ошибок в файл
func setupLogging(file *os.File) {
	// Перенаправляем стандартный вывод и ошибки
	//os.Stdout = file
	//os.Stderr = file

	// Настроить логгер для записи в файл
	logging.Debug.SetOutput(file)
	logging.Info.SetOutput(file)
	logging.Warn.SetOutput(file)
	logging.Err.SetOutput(file)
	log.SetOutput(file)
}

//export StartOutline
func StartOutline(key *C.char) {
	str_key := C.GoString(key)
	keyPtr := &str_key

	app := App{
		TransportConfig: keyPtr,
		RoutingConfig: &RoutingConfig{
			TunDeviceName:        "outline233",
			TunDeviceIP:          "10.233.233.1",
			TunDeviceMTU:         1500,
			TunGatewayCIDR:       "10.233.233.2/32",
			RoutingTableID:       233,
			RoutingTablePriority: 23333,
			DNSServerIP:          "9.9.9.9",
		},
	}

	// Открытие файла для логов
	logFile, err := initLogToFile()
	if err != nil {
		logging.Err.Printf("Failed to initialize log file: %v\n", err)
		return
	}
	defer logFile.Close()

	// Настройка перенаправления вывода
	setupLogging(logFile)

	ctx, cancel := context.WithCancel(context.Background())
	cancelFunc = cancel

	if err := app.Run(ctx); err != nil {
		logging.Err.Printf("%v\n", err)
	}

	// Обязательно flush, чтобы убедиться, что все логи записаны
	logFile.Sync()
}

//export StopOutline
func StopOutline() {
	if cancelFunc != nil {
		cancelFunc()
	}
}

func main() {}
