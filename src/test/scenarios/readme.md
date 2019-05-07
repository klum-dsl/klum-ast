This folder can be used for quick tests against real models

All folders starting with _ are automatically ignored, this is usefull
for testing with private models.

Each folder under this folder is evaluated the following way:

First all subfolders are compiled in lexical order.

Next, an optional assert.groovy is evaluated.