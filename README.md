Mac builds are not notarised. Follow [these instructions](https://support.apple.com/en-gb/guide/mac-help/mh40616/mac) to run them.
Altenatively, run spctl --add appName in a terminal ([Source](https://osxdaily.com/2015/07/15/add-remove-gatekeeper-app-command-line-mac-os-x/)).
If you receive an error "DobbyVPN" is damaged and can't be opened, run xattr -cr appName ([source](https://www.youtube.com/watch?v=6fqzb4qpgcs))
