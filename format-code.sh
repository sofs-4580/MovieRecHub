set -e

# Ensure the .cache directory exists and download google-java-format if needed.
mkdir -p .cache
cd .cache
if [ ! -f google-java-format-1.25.2-all-deps.jar ]; then
    echo "Downloading google-java-format..."
    curl -LJO "https://github.com/google/google-java-format/releases/download/v1.25.2/google-java-format-1.25.2-all-deps.jar"
    chmod 755 google-java-format-1.25.2-all-deps.jar
fi
cd ..

# Get the list of staged Java files.
changed_java_files=$(git diff --cached --name-only --diff-filter=ACMR | grep "\.java$")
if [ -z "$changed_java_files" ]; then
  echo "No Java files staged for formatting."
  exit 0
fi

echo "Formatting Java files: $changed_java_files"
java -jar .cache/google-java-format-1.25.2-all-deps.jar --replace $changed_java_files
