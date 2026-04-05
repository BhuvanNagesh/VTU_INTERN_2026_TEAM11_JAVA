const fs = require('fs');
const https = require('https');
const { execSync } = require('child_process');

console.log('Downloading Maven...');
const file = fs.createWriteStream('../maven.zip');
https.get('https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip', function(res) {
  res.pipe(file);
  file.on('finish', function() {
    file.close(() => {
      console.log('Downloaded. Extracting...');
      execSync('powershell.exe -NoProfile -NonInteractive -Command "Expand-Archive -Path ../maven.zip -DestinationPath ../ -Force"', {stdio: 'inherit'});
      console.log('Extracted Maven successfully');
    });
  });
}).on('error', function(err) {
  fs.unlink('../maven.zip');
  console.error('Error downloading:', err.message);
});
