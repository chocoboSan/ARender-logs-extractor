# ARender-logs-extractor

This program expects : 
 - A list of folders as argument (protect your folder names) 
    - those folders are to be containing subfolders which correspond, by name, to each ARender 3 rendition servers and their logs
    
Following that syntax this program outputs to stdout : 
 - the list of IDs (b64) common to all provided log folders
    - this searches for incorrect documents
 - the corresponding time of appearance of those IDs in the "server logs"
 - an approximate number of document parsed per second per server and an average per log folder
    - this is based on the method BaseDocumentService.getDocumentLayout
 - an average image width in px per server, and in average
 - a distribution, in percentage of the image width in px, per stepping of 100px
 - a distribution of mimeTypes found, in percentage. 
 
  