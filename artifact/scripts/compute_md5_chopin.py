#!/usr/bin/python3

import sys
from os.path import join
from os import listdir,rename
import re
import hashlib

root = sys.argv[1]
md5dir = join(root, "META-INF", "md5")

#jarPattern=re.compile("([0-9a-f]+) (jar.*)")
pattern=re.compile("([0-9a-f]+) (.*)\.(.*)")
exts={'jar', 'zip', 'class'}

for md5 in listdir(md5dir):
    tmpfile=md5+".tmp"
    with open(tmpfile,'w') as result:
        with open(join(md5dir,md5)) as md5file:
            for line in md5file.readlines():
                match = pattern.search(line)
                if match and match.group(3) in exts:
                    data=open(join(root,match.group(2)+'.'+match.group(3)), 'rb').read()
                    updatedMD5=hashlib.md5(data).hexdigest()
                    result.write("{} {}.{}\n".format(updatedMD5, match.group(2), match.group(3)))
                else:
                    result.write(line)
    rename(tmpfile,join(md5dir,md5))
