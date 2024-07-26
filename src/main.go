package main

import (
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"
)


func main() {
	a := app.New()
	w := a.NewWindow("DobbyVPN")

	statusLabel := widget.NewLabel("Not connected")
	connected := false
	var connectBtn *widget.Button
	connectBtn = widget.NewButton("Connect", func() {
		if connected {
			connectBtn.SetText("Disconnecting...")
			//TODO
			connected = false
			connectBtn.SetText("Connect")
			statusLabel.SetText("Not connected")
		} else {
			connectBtn.SetText("Connecting...")
			//TODO
			connected = true
			connectBtn.SetText("Disconnect")
			statusLabel.SetText("Connected")
		}
	})
	configInput := widget.NewEntry()
	configInput.MultiLine = true
	configInput.SetPlaceHolder("Input config...")

	w.SetContent(container.NewVBox(
		connectBtn,
		statusLabel,
		configInput,
	))

	w.ShowAndRun()
}
