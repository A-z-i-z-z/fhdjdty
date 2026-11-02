# Test fixtures

`synthetic_garnet.txt` is an **invented** paper written to test the pipeline end to end. Its
compositions and numbers are made up and must never enter the dataset. It is laid out like a
pdftotext dump and contains deliberate traps:

- a value quoted from another paper in the introduction (should be `literature_cited`),
- an electronic conductivity (should not be extracted),
- a figure-only claim with no number (should not be extracted),
- a table with three compositions (three records),
- a bulk value next to the total value for the same sample,
- a PDF line-break hyphen inside a quote-worthy sentence.

The warning lives here rather than in the text because the extractor, correctly, refused to
extract from a document that said its numbers were invented.
