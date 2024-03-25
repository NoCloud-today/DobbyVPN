import 'package:flutter/material.dart';

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
          left: _offset = (_offset == 0 ? -1000 : 0),
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
                width: MediaQuery.of(context).size.width * 0.5,
                height: MediaQuery.of(context).size.width * 0.5,

                child: ElevatedButton(
                  onPressed: _incrementCounter,
                  style: ButtonStyle(
                    shape: MaterialStateProperty.all(
                    RoundedRectangleBorder(borderRadius: BorderRadius.circular(100),)),
                    minimumSize: MaterialStateProperty.all(MediaQuery.of(context).size * 0.4),
                    maximumSize: MaterialStateProperty.all(MediaQuery.of(context).size * 0.6),
                    backgroundColor: MaterialStateProperty.all(Colors.blueGrey),
                  ),

                  child: Text(isOn ? 'ON' : "OFF",
                    style: const TextStyle(
                    fontSize: 40, color: Colors.white)
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
