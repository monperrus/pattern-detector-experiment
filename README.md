# Pattern Detector Experiment

This repository contains the data and the scripts to identify patterns automatically in benchmarks of diffs.
The result produced by this repository is available at this address: https://durieux.me/pattern-detector-experiment/.
It allows us to navigate and filter the diffs to have a better understanding of the benchmarks.


## How to create a new dissection?

Install <https://github.com/lascam-UFU/automatic-diff-dissection>

```bash
git clone https://github.com/lascam-UFU/automatic-diff-dissection
cd automatic-diff-dissection
mvn install
```

Run [extract_feature.py](https://github.com/tdurieux/pattern-detector-experiment/blob/master/script/extract_feature.py). This creates one JSON file per patch:

```bash
python extract_feature.py <path to benchmark> <path to automatic-diff-dissection> <output folder>
python extract_feature.py ../benchmark/defects4j/ ../../automatic-diff-dissection/ output
```

Run [merge_features.py](https://github.com/tdurieux/pattern-detector-experiment/blob/master/script/merge_features.py), this will create a unique file `dissection.json` with the output folder of the previous step as input

```bash
python merge_features.py <benchmark_path> <benchmark_name> <folder_json>
python merge_features.py ../../benchmarks/ defects4j output
```

Put the file in `docs/data` and add the name of the file in [app.js](https://github.com/tdurieux/pattern-detector-experiment/blob/master/docs/js/app.js#L200)

```bash
cp ../../benchmarks/defects4j/dissection.js defects4j.json
vi docs/js/apps.js
```

Open the file in a web browser (does not work if you open the file directly):

```bash
cd docs
python -m SimpleHTTPServer
firefox http://localhost:8000
```


