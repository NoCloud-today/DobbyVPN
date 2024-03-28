import 'package:flutter/material.dart';
import 'package:auto_size_text/auto_size_text.dart';
import 'dart:io' show Platform;


void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Dobby VPN',
      theme: ThemeData(
        colorScheme: const ColorScheme.dark(),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Dobby VPN'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool isOn = false;
  double _offset = 0.0;

  void _incrementCounter() {
    setState(() {
      isOn = !isOn;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBodyBehindAppBar: true,
      backgroundColor: Colors.white,
      appBar: AppBar(
      backgroundColor: Colors.blueGrey,
      title: Text(widget.title),
      ),

      body: Stack(children: [
        AnimatedPositioned(
          duration: Duration(seconds: 100),
          left: (Platform.isAndroid || Platform.isIOS) ? _offset = (_offset == 0 ? -800 : 0) : 0,
          height: MediaQuery.of(context).size.height,
          child: Image.asset('background.png', fit: BoxFit.cover),
        ),
        Center(
          child: Container(

            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.blueGrey,
              border: Border.all(color: Colors.white38, width: 6)),

              child: SizedBox(
                width: 200,
                height: 200,

                child: ElevatedButton(
                  onPressed: _incrementCounter,
                  style: ButtonStyle(
                    shape: MaterialStateProperty.all(
                    RoundedRectangleBorder(borderRadius: BorderRadius.circular(110),)),
                    minimumSize: MaterialStateProperty.all(MediaQuery.of(context).size * 0.2),
                    maximumSize: MaterialStateProperty.all(MediaQuery.of(context).size * 0.5),
                    backgroundColor: MaterialStateProperty.all(Colors.blueGrey),
                  ),

                  child: AutoSizeText(isOn ? 'ON' : "OFF",
                    style: const TextStyle(
                      fontSize: 30, color: Colors.white),
                      maxLines: 1
                  )
                )
              )
            )
          )
        ]
      )
    );
  }
}
