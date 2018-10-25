#!/usr/bin/env python3

import os
import os.path
import re
import fileinput

imagesets = [f for f in os.listdir(".") if f.endswith(".imageset")]

for imageset in imagesets:
    setname = imageset.replace('.imageset', '')
    images = [f for f in os.listdir(imageset) if f.endswith(".png")]
    contents = []
    with open(os.path.join(imageset, 'Contents.json'), 'r') as contentsFile:
        contents = contentsFile.readlines()

    for image in images:
        if '@' not in image:
            newname = image.replace('.png', '@1x.png')
            path = os.path.join(imageset, image)
            newpath = os.path.join(imageset, newname)
            os.rename(path, newpath)
            contents = [line.replace(image, newname) for line in contents]

    with open(os.path.join(imageset, 'Contents.json'), 'w') as contentsFile:
        contentsFile.writelines(contents)

