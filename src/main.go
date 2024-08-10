package main

import (
	"os"
	"os/exec"

	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"
)


const config_path = "ckclient.json"


func main() {
	a := app.New()
	w := a.NewWindow("DobbyVPN")

	connected := false
	statusLabel := widget.NewLabel("Not connected")
	configInput := widget.NewEntry()
	configInput.MultiLine = true
	configInput.SetPlaceHolder("Input config...")
	var connectBtn *widget.Button
	connectBtn = widget.NewButton("Connect", func() {
		if connected {
			connectBtn.SetText("Disconnecting...")
			stop_cloak()
			connected = false
			connectBtn.SetText("Connect")
			statusLabel.SetText("Not connected")
		} else {
			if configInput.Text != "" {
				f, err := os.Create(config_path)
				if err != nil {
					panic(err)
				}
				f.WriteString(configInput.Text)
				f.Close()
			}
			connectBtn.SetText("Connecting...")
			start_cloak()
			connected = true
			connectBtn.SetText("Disconnect")
			statusLabel.SetText("Connected")
		}
	})

	w.SetContent(container.NewVBox(
		connectBtn,
		statusLabel,
		configInput,
	))

	w.ShowAndRun()
}


func start_cloak() {
	cmnd := exec.Command("./ck-client", "-l", "1984", "-u", "-c", config_path)
	cmnd.Start()
}


func stop_cloak() {
	cmnd := exec.Command("taskkill", "/im", "ck-client.exe", "/f")
	cmnd.Start()
}
