# scripts/build-core.ps1 — Windows twin of build-core.sh (gomobile AAR).
param([string]$Out = "", [string]$MobileVersion = "v0.0.0-20260209203831-923679eb55af")
$ROOT = Split-Path (Split-Path $MyInvocation.MyCommand.Path -Parent) -Parent
if ($Out -eq "") { $Out = Join-Path $ROOT "android\app\libs" }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
# Pinned x/mobile (go 1.24 line; @latest needs go>=1.26). See build-core.sh.
go install "golang.org/x/mobile/cmd/gomobile@$MobileVersion"
Push-Loc (Join-Path $ROOT "core\upstream")
go get -tool "golang.org/x/mobile/cmd/gobind@$MobileVersion"
gomobile init
# NDK r28+ required for 16 KB page-size alignment (Play-enforced for Android 15+ targets).
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" `
  -o "$Out\libgopeed.aar" -target=android -androidapi 21 -javapkg="com.gopeed" `
  github.com/GopeedLab/gopeed/bind/mobile
Pop-Loc
Write-Host "AAR -> $Out\libgopeed.aar"
