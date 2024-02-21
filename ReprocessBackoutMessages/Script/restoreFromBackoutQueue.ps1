# Define the output directory
$outputDirectory = "E:\API_TRIGGER\RESTOREBACKOUT\"  # Change this to your desired directory

# Define the exception list where you specify original queues for specific backout queues
$exceptionList = @{
    "BACKOUT.QUEUE1" = "ORIGINAL.QUEUE1"
    # Add more exceptions as needed
}

# Get a list of all queues with depth > 1 using runmqsc
$mqscOutput = & echo "dis ql(*) where(curdepth ge 1)" | runmqsc

# Split the MQSC output into lines
$lines = $mqscOutput -split [Environment]::NewLine

# Initialize a hashtable to store the results
$results = @{}

# Process each line
foreach ($line in $lines) {
    # Check if the line matches the pattern for displaying queue information
    if ($line -match ".*?\((.*?(\.BACKOUT|_BACKOUT))\).*") {
        $queueName = $matches[1]

        # Check if the queue name ends with ".BACKOUT" and it's not in the exception list
        if (-not $exceptionList.ContainsKey($queueName)) {
            $originalQueue = $queueName -replace "(\.BACKOUT|_BACKOUT)$", ""

            # Add the result to the hashtable
            $results[$queueName] = $originalQueue
        } else {
            $originalQueue = $exceptionList[$queueName]

            # Add the result to the hashtable
            $results[$queueName] = $originalQueue
        }
    }
}

# Iterate through the hashtable and save each result as a JSON file
foreach ($queueName in $results.Keys) {
    $originalQueue = $results[$queueName]
    $outputFileName = Join-Path -Path $outputDirectory -ChildPath "$queueName.json"
    $outputData = @{
        "inQ" = $queueName
        "outQ" = $originalQueue
    } | ConvertTo-Json
    [System.IO.File]::WriteAllText($outputFileName, $outputData, (New-Object System.Text.UTF8Encoding $False))
}

# Output the directory where the JSON files were saved
Write-Output "JSON files saved in: $outputDirectory"
