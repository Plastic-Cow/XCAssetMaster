package com.goodnighttales.xcassetmaster.util

operator fun String.not(): String {
    return "\u001B[" + this
}

/*
!#;#m
https://stackoverflow.com/a/33206814/1541907
╔══════════╦════════════════════════════════╦═════════════════════════════════════════════════════════════════════════╗
║  Code    ║             Effect             ║                                   Note                                  ║
╠══════════╬════════════════════════════════╬═════════════════════════════════════════════════════════════════════════╣
║ 0        ║  Reset / Normal                ║  all attributes off                                                     ║
║ 1        ║  Bold or increased intensity   ║                                                                         ║
║ 2        ║  Faint (decreased intensity)   ║  Not widely supported.                                                  ║
║ 3        ║  Italic                        ║  Not widely supported. Sometimes treated as inverse.                    ║
║ 4        ║  Underline                     ║                                                                         ║
║ 5        ║  Slow Blink                    ║  less than 150 per minute                                               ║
║ 6        ║  Rapid Blink                   ║  MS-DOS ANSI.SYS; 150+ per minute; not widely supported                 ║
║ 7        ║  [[reverse video]]             ║  swap foreground and background colors                                  ║
║ 8        ║  Conceal                       ║  Not widely supported.                                                  ║
║ 9        ║  Crossed-out                   ║  Characters legible, but marked for deletion.  Not widely supported.    ║
║ 10       ║  Primary(default) font         ║                                                                         ║
║ 11–19    ║  Alternate font                ║  Select alternate font `n-10`                                           ║
║ 20       ║  Fraktur                       ║  hardly ever supported                                                  ║
║ 21       ║  Bold off or Double Underline  ║  Bold off not widely supported; double underline hardly ever supported. ║
║ 22       ║  Normal color or intensity     ║  Neither bold nor faint                                                 ║
║ 23       ║  Not italic, not Fraktur       ║                                                                         ║
║ 24       ║  Underline off                 ║  Not singly or doubly underlined                                        ║
║ 25       ║  Blink off                     ║                                                                         ║
║ 27       ║  Inverse off                   ║                                                                         ║
║ 28       ║  Reveal                        ║  conceal off                                                            ║
║ 29       ║  Not crossed out               ║                                                                         ║
║ 30–37    ║  Set foreground color          ║  See color table below                                                  ║
║ 38       ║  Set foreground color          ║  Next arguments are `5;n` or `2;r;g;b`, see below                       ║
║ 39       ║  Default foreground color      ║  implementation defined (according to standard)                         ║
║ 40–47    ║  Set background color          ║  See color table below                                                  ║
║ 48       ║  Set background color          ║  Next arguments are `5;n` or `2;r;g;b`, see below                       ║
║ 49       ║  Default background color      ║  implementation defined (according to standard)                         ║
║ 51       ║  Framed                        ║                                                                         ║
║ 52       ║  Encircled                     ║                                                                         ║
║ 53       ║  Overlined                     ║                                                                         ║
║ 54       ║  Not framed or encircled       ║                                                                         ║
║ 55       ║  Not overlined                 ║                                                                         ║
║ 60       ║  ideogram underline            ║  hardly ever supported                                                  ║
║ 61       ║  ideogram double underline     ║  hardly ever supported                                                  ║
║ 62       ║  ideogram overline             ║  hardly ever supported                                                  ║
║ 63       ║  ideogram double overline      ║  hardly ever supported                                                  ║
║ 64       ║  ideogram stress marking       ║  hardly ever supported                                                  ║
║ 65       ║  ideogram attributes off       ║  reset the effects of all of 60-64                                      ║
║ 90–97    ║  Set bright foreground color   ║  aixterm (not in standard)                                              ║
║ 100–107  ║  Set bright background color   ║  aixterm (not in standard)                                              ║
╚══════════╩════════════════════════════════╩═════════════════════════════════════════════════════════════════════════╝

╔═════════╦════════════════════════════════════════╦═══════════════════════════════════════════════════════════════════╗
║  Code   ║                 Effect                 ║                                Note                               ║
╠═════════╬════════════════════════════════════════╬═══════════════════════════════════════════════════════════════════╣
║ ^[ m    ║ Turn off character attributes          ║                                                                   ║
║ ^[ 0m   ║ Turn off character attributes          ║                                                                   ║
║ ^[ 1m   ║ Turn bold mode on                      ║                                                                   ║
║ ^[ 2m   ║ Turn low intensity mode on             ║                                                                   ║
║ ^[ 4m   ║ Turn underline mode on                 ║                                                                   ║
║ ^[ 5m   ║ Turn blinking mode on                  ║                                                                   ║
║ ^[ 7m   ║ Turn reverse video on                  ║                                                                   ║
║ ^[ 8m   ║ Turn invisible text mode on            ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^[ @;@r ║ Set top and bottom lines of a window   ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^[ @A   ║ Move cursor up n lines                 ║                                                                   ║
║ ^[ @B   ║ Move cursor down n lines               ║                                                                   ║
║ ^[ @C   ║ Move cursor right n lines              ║                                                                   ║
║ ^[ @D   ║ Move cursor left n lines               ║                                                                   ║
║ ^[ H    ║ Move cursor to upper left corner       ║                                                                   ║
║ ^[ ;H   ║ Move cursor to upper left corner       ║                                                                   ║
║ ^[ @;@H ║ Move cursor to screen location v,h     ║                                                                   ║
║ ^[ f    ║ Move cursor to upper left corner       ║                                                                   ║
║ ^[ ;f   ║ Move cursor to upper left corner       ║                                                                   ║
║ ^[ @;@f ║ Move cursor to screen location v,h     ║                                                                   ║
║ ^  D    ║ Move/scroll window up one line         ║                                                                   ║
║ ^  M    ║ Move/scroll window down one line       ║                                                                   ║
║ ^  E    ║ Move to next line                      ║                                                                   ║
║ ^  7    ║ Save cursor position and attributes    ║                                                                   ║
║ ^  8    ║ Restore cursor position and attributes ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^  H    ║ Set a tab at the current column        ║                                                                   ║
║ ^[ g    ║ Clear a tab at the current column      ║                                                                   ║
║ ^[ 0g   ║ Clear a tab at the current column      ║                                                                   ║
║ ^[ 3g   ║ Clear all tabs                         ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^# 3    ║ Double-height letters, top half        ║                                                                   ║
║ ^# 4    ║ Double-height letters, bottom half     ║                                                                   ║
║ ^# 5    ║ Single width, single height letters    ║                                                                   ║
║ ^# 6    ║ Double width, single height letters    ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^[ K    ║ Clear line from cursor right           ║                                                                   ║
║ ^[ 0K   ║ Clear line from cursor right           ║                                                                   ║
║ ^[ 1K   ║ Clear line from cursor left            ║                                                                   ║
║ ^[ 2K   ║ Clear entire line                      ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^[ J    ║ Clear screen from cursor down          ║                                                                   ║
║ ^[ 0J   ║ Clear screen from cursor down          ║                                                                   ║
║ ^[ 1J   ║ Clear screen from cursor up            ║                                                                   ║
║ ^[ 2J   ║ Clear entire screen                    ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^  5n   ║ Device status report                   ║                                                                   ║
║ ^  0n   ║ Response: terminal is OK               ║                                                                   ║
║ ^  3n   ║ Response: terminal is not OK           ║                                                                   ║
║         ║                                        ║                                                                   ║
║ ^  6n   ║ Get cursor position                    ║                                                                   ║
║ ^  @;@R ║ Response: cursor is at v,h             ║                                                                   ║
║ ^  c    ║                                        ║                                                                   ║
╚═════════╩════════════════════════════════════════╩═══════════════════════════════════════════════════════════════════╝
 */
