# Build script for Java Burp Extension
# This script finds Burp Suite JAR and builds the extension

$ErrorActionPreference = "Stop"

Write-Host "Building Zlib Handler Burp Extension..." -ForegroundColor Green

$burpJar = $null
if (Test-Path "lib\burpsuite_pro.jar") {
    $burpJar = (Resolve-Path "lib\burpsuite_pro.jar").Path
    Write-Host "Found existing Burp Suite JAR: $burpJar" -ForegroundColor Green
} else {
    $burpDir = "C:\Users\${username}\AppData\Local\BurpSuitePro\"
    
    $allJars = Get-ChildItem -Path $burpDir -Filter "*.jar" -ErrorAction SilentlyContinue
    foreach ($jar in $allJars) {
        $name = $jar.Name.ToLower()
        if ($name -notlike "*loader*" -and $name -notlike "*keygen*" -and 
            ($name -like "burpsuite*" -or $name -like "burp*")) {
            $burpJar = $jar.FullName
            Write-Host "Found Burp Suite JAR: $burpJar" -ForegroundColor Yellow
            break
        }
    }
    
    if (-not $burpJar) {
        Write-Host "ERROR: Burp Suite JAR not found!" -ForegroundColor Red
        Write-Host "Please manually copy Burp Suite JAR to: lib\burpsuite_pro.jar" -ForegroundColor Yellow
        exit 1
    }
}

# Create lib directory if it doesn't exist
if (-not (Test-Path "lib")) {
    New-Item -ItemType Directory -Path "lib" | Out-Null
}
if (-not (Test-Path "lib\burpsuite_pro.jar")) {
    Copy-Item $burpJar -Destination "lib\burpsuite_pro.jar" -Force
    Write-Host "Copied JAR to lib\burpsuite_pro.jar" -ForegroundColor Green
}
# Create local Maven repository structure
$repoDir = "lib\net\portswigger\burp\extensions\burp-extensions-api\1.0"
if (-not (Test-Path $repoDir)) {
    New-Item -ItemType Directory -Path $repoDir -Force | Out-Null
    Write-Host "Created local repository directory: $repoDir" -ForegroundColor Green
}

# Copy JAR to repository
$repoJar = Join-Path $repoDir "burp-extensions-api-1.0.jar"
if (-not (Test-Path $repoJar)) {
    Copy-Item "lib\burpsuite_pro.jar" -Destination $repoJar -Force
    Write-Host "Copied JAR to repository: $repoJar" -ForegroundColor Green
}

# Create pom.xml for the repository
$repoPom = Join-Path $repoDir "burp-extensions-api-1.0.pom"
if (-not (Test-Path $repoPom)) {
    $pomContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>net.portswigger.burp.extensions</groupId>
    <artifactId>burp-extensions-api</artifactId>
    <version>1.0</version>
    <packaging>jar</packaging>
    <name>Burp Suite Extensions API</name>
    <description>Burp Suite Extensions API for building extensions</description>
</project>
"@
    Set-Content -Path $repoPom -Value $pomContent -Encoding UTF8
    Write-Host "Created pom.xml in repository" -ForegroundColor Green
}

# Verify JAR contains Burp Suite API classes
Write-Host "Verifying Burp Suite API JAR..." -ForegroundColor Yellow
$jarPath = "lib\burpsuite_pro.jar"
if (Test-Path $jarPath) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
        $hasIBurpExtender = $false
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like "burp/IBurpExtender.class" -or $entry.FullName -like "burp/IBurpExtender*.class") {
                $hasIBurpExtender = $true
                break
            }
        }
        $zip.Dispose()
        
        if (-not $hasIBurpExtender) {
            Write-Host "ERROR: JAR file does not contain Burp Suite API classes (IBurpExtender not found)!" -ForegroundColor Red
            Write-Host "Please ensure you're using the correct Burp Suite JAR file." -ForegroundColor Yellow
            Write-Host "The JAR should contain burp/IBurpExtender.class and other burp.* classes." -ForegroundColor Yellow
            exit 1
        } else {
            Write-Host "JAR verification successful - contains Burp Suite API classes" -ForegroundColor Green
        }
    } catch {
        Write-Host "WARNING: Could not verify JAR contents: $_" -ForegroundColor Yellow
    }
}

# Find Maven
$mavenPaths = @(
    "$env:USERPROFILE\Tools\maven\bin\mvn.cmd",
    "C:\Program Files\Apache\maven\bin\mvn.cmd",
    "C:\apache-maven\bin\mvn.cmd",
    "mvn.cmd"
)

$mvn = $null
foreach ($path in $mavenPaths) {
    if (Test-Path $path) {
        $mvn = $path
        break
    }
    # Try to find in PATH
    $found = Get-Command "mvn" -ErrorAction SilentlyContinue
    if ($found) {
        $mvn = "mvn"
        break
    }
}

# Maven이 없으면 오류
if (-not $mvn) {
    Write-Host "ERROR: Maven not found!" -ForegroundColor Red
    Write-Host "Please install Maven and ensure it's in your PATH." -ForegroundColor Yellow
    Write-Host "Download from: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
    exit 1
}

Write-Host "Using Maven: $mvn" -ForegroundColor Yellow

# Install JAR to local Maven repository (~/.m2/repository) using maven-install-plugin
Write-Host "Installing Burp Suite API to local Maven repository..." -ForegroundColor Yellow
$installArgs = @(
    "install:install-file",
    "-Dfile=lib\burpsuite_pro.jar",
    "-DgroupId=net.portswigger.burp.extensions",
    "-DartifactId=burp-extensions-api",
    "-Dversion=1.0",
    "-Dpackaging=jar",
    "-DgeneratePom=true"
)
& $mvn $installArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: Failed to install JAR to local Maven repository. Continuing anyway..." -ForegroundColor Yellow
}

# Build
Write-Host "Building project..." -ForegroundColor Green
& $mvn clean package -U

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nBuild successful!" -ForegroundColor Green
    Write-Host "JAR file location: target\zlib-handler-1.0.1.jar" -ForegroundColor Cyan
    Write-Host "`nTo install in Burp Suite:" -ForegroundColor Yellow
    Write-Host "1. Open Burp Suite" -ForegroundColor White
    Write-Host "2. Go to Extender > Extensions" -ForegroundColor White
    Write-Host "3. Click Add" -ForegroundColor White
    Write-Host "4. Select Extension type: Java" -ForegroundColor White
    Write-Host "5. Select file: target\zlib-handler-1.0.1.jar" -ForegroundColor White
} else {
    Write-Host "`nBuild failed!" -ForegroundColor Red
    exit 1
}
