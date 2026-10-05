@echo off
start "" http://localhost:4173
node "%~dp0scripts\backlog-dashboard.mjs" %*
