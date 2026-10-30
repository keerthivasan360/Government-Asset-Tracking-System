Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " AssetGuard - Pushing Codebase to GitHub" -ForegroundColor Green
Write-Host " Repository: https://github.com/keerthivasan360/Government-Asset-Tracking-System.git" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
git branch -M main
git push -u origin main
Write-Host "===================================================" -ForegroundColor Cyan
Read-Host "Press Enter to exit..."
