#!/usr/bin/python3
import sys
import re
from os import path

bms = ("avrora", "batik", "fop", "h2", "h2cs", "jython", "luindex", "lusearch", "pmd", "sunflow", "xalan", "jme")
infolder = '.'

def convert_size(size_str):
    '''Convert xM into an int of x   
    M - MBs, G - GBs
    '''
    unit = size_str[-1]
    num = int(size_str[:-1])
    if(unit == 'M'):
        return num
    elif(unit == 'G'):
        return 1024 * num
    else:
        raise Exception(f"{unit} conversion not coded in")

def get_compressed(file="compressed_sizes.txt"):
    '''Did a quick n=1 test to see the compressed sizes
    But we need to pull these from another file
    To be easier, I grepped the values out before and stored in format:
    <benchmark key> <size>M
    '''
    with open(file, "r") as f:
        lines = f.readlines()
        splits = [line.split(' ') for line in lines]
        return {bench: convert_size(size) for (bench, size) in splits}

def get_mem(file):
    runs = []
    used = 0
    total = 0
    capture = False
    with open(file, "r") as f:
        line = f.readline()
        while line:
            if(capture):
                #should match PSYoungGen and ParOldGen
                #are the two GCs responsible for the heap
                #Metaspace is space for classes and metadata
                #needs a different pattern
                #this assumes units are always the same...
                if(m := re.search(r'(\w+)\s+total (\d+)K, used (\d+)K', line)):
                    #print("MATCH", line, end="")
                    used += int(m.group(3))
                    total += int(m.group(2))
                elif(line.startswith("Heap:")):
                    line = f.readline() #heap data here
                    #committed is the current amount alloc'ed to the jvm by the os
                    #max with this method is always the param we set...
                    #total is just the variable used in the other scripts
                    #this will compare used values for all benchmarks reported this way..
                    if(m := re.search(r'used = \d+\((\d+)K\) committed = \d+\((\d+)K\)', line)):
                        #used += int(m.group(1))
                        total += int(m.group(2)) 
                    f.readline() #Non-heap:
                    line = f.readline() #non heap data here
                    if(m := re.search(r'committed = \d+\((\d+)K\)', line)):
                        total += int(m.group(1))
                        #max is always -1 for this
                        #total += int(m.group(2))
                    #next iteration from here on
                    runs.append({"used": used, "total": total, "prop": used / total})
                    used = 0
                    total = 0
                    capture = False 
                elif(m := re.search(r'Metaspace\s+used (\d+)K.*reserved (\d+)K', line)):
                    #print("MATCH AND RESET", line, end="")
                    used += int(m.group(1))
                    total += int(m.group(2))
                    #Metaspace is the last one we care about, so reset here
                    runs.append({"used": used, "total": total, "prop": used / total})
                    used = 0
                    total = 0
                    capture = False
            elif("PASSED" in line or "TCP server" in line):
                #print("CAPTURE")
                #make special exception for h2, b/c server cannot tell us if it passed
                #benchmark passed, we can collect data
                capture = True
            line = f.readline()

    return runs

def read_benches(mode):
    exps = {}
    for bench in bms:
        file = path.join(infolder, f"{mode}-{bench}.txt")
        if(path.exists(file)):
            exps[bench] = get_mem(file)

    return exps

def avg(data, key="total"):
    avgs = {}
    for bench in bms:
        try:
            runs = data[bench]
            n = len(runs)
            avg = 0
            if(n != 0):
                avg = sum(map(lambda x: x[key], runs))/n
            avgs[bench] = avg
        except KeyError:
            pass
    return avgs

def avg_maxes(data):
    if(len(data) == 0):
        return 0
    return sum(data.values())/len(data)

def max_measured(data, key="total"):
    maxes = {}
    for bench in bms:
        try:
            runs = data[bench]
            n = len(runs)
            m = 0
            if(n != 0):
                m = max(map(lambda x: x[key], runs))
            maxes[bench] = m
        except KeyError:
            pass
    return maxes

def max_overhead(vanilla, other, key="total"):
    v_m = max_measured(vanilla, key=key)
    o_m = max_measured(other, key=key)
    return {b: norm(o_m[b], v_m[b]) for b in bms if b in vanilla and b in other}

def avg_overhead(vanilla, other, key="total"):
    avg_norms = {}
    for bench in bms:
        try:
            oth_runs = other[bench]
            van_runs = vanilla[bench]
            if(len(oth_runs) == 0 or len(van_runs) == 0):
                avg_norms[bench] = 0
            else:
                get_key = lambda x: x[key]
                oth_avg = sum(map(get_key, oth_runs))/len(oth_runs)
                van_avg = sum(map(get_key, van_runs))/len(van_runs)
                avg_norms[bench] = oth_avg/van_avg
        except KeyError:
            pass
    return avg_norms

def norm(a, b):
    if(b == 0):
        return 0
    return a/b

def make_table_body(vanilla, *others, bench_fmt="{:s}", fmt="{:.2f}", sep=","):
    #van_avg = avg(vanilla)
    van_avg=max_measured(vanilla)
    avgs=[max_measured(o) for o in others]
    #avgs = [avg(o) for o in others]
    n = len(others)
    data_cols = 2 * (n)  + 1
    for bench in bms:
        print(bench_fmt.format(bench), end=sep)
        print(fmt.format(van_avg.get(bench, 0)), end=sep)
        for i in range(n - 1):
            print(fmt.format(avgs[i].get(bench, 0)), end=sep)
            print(fmt.format(norm(avgs[i].get(bench, 0), van_avg.get(bench, 1))), end=sep)
        #sep changes for second
        print(fmt.format(avgs[-1].get(bench, 0)), end=sep)
        print(fmt.format(norm(avgs[-1].get(bench, 0), van_avg.get(bench, 1))))

def make_table_head(keys, sep=",", bench_fmt="{:s}", col_fmt="{:s}"):
    print(bench_fmt.format("experiment"), end=sep)
    print(col_fmt.format("vanilla"), end=sep)
    for k in keys[:-1]:
        print(col_fmt.format(k), end = sep)
        print(col_fmt.format(k + "_over"), end = sep)
    print(col_fmt.format(keys[-1]), end=sep)
    print(col_fmt.format(keys[-1] + "_over"))

def make_table_csv(vanilla, others, keys):
    make_table_head(keys)
    make_table_body(vanilla, *others)

def make_table_txt(vanilla, others, keys):
    text_cell = "{:15s}"
    float_cell = "{:15.2f}"
    make_table_head(keys, bench_fmt=text_cell, col_fmt=text_cell, sep="|")
    make_table_body(vanilla, *others, bench_fmt=text_cell, fmt=float_cell, sep="|")

if __name__ == "__main__":
    infolder = sys.argv[1]
    mode = sys.argv[2]
    fmt = sys.argv[3]
    
    make = make_table_txt
    if(fmt == "csv"):
        make = make_table_csv


    if(mode == "rec"):
        make(read_benches("vanilla-rec"), [read_benches("jmvx-rec-nosync"), read_benches("jmvx-rec")], ["nosync", "sync"])
    elif(mode == "rep"):
        make(read_benches("vanilla-rec"), [read_benches("jmvx-rep-nosync"), read_benches("jmvx-rep")], ["nosync", "sync"])
    elif(mode == "mvx"):
        make(read_benches("vanilla"), [read_benches("jmvx-leader"), read_benches("jmvx-follower")], ["leader", "follower"])
    else:
        print("Unrecognized mode")
        

