#!/usr/bin/python3

import re
import numpy as np
import sys
from os.path import exists, join
from math import isnan
import table_mem as mem


files = [
        "vanilla-avrora.txt",
        "vanilla-batik.txt",
        "vanilla-fop.txt",
        "vanilla-h2.txt",
        "vanilla-h2cs.txt",
        "vanilla-jme.txt",
        "vanilla-jython.txt",
        "vanilla-luindex.txt",
        "vanilla-lusearch.txt",
        "vanilla-pmd.txt",
        "vanilla-sunflow.txt",
        "vanilla-xalan.txt",
        "jmvx-follower-avrora.txt",
        "jmvx-follower-batik.txt",
        "jmvx-follower-fop.txt",
        "jmvx-follower-h2.txt",
        "jmvx-follower-h2cs.txt",
        "jmvx-follower-jme.txt",
        "jmvx-follower-jython.txt",
        "jmvx-follower-luindex.txt",
        "jmvx-follower-lusearch.txt",
        "jmvx-follower-pmd.txt",
        "jmvx-follower-sunflow.txt",
        "jmvx-follower-xalan.txt",
        "jmvx-leader-avrora.txt",
        "jmvx-leader-batik.txt",
        "jmvx-leader-fop.txt",
        "jmvx-leader-h2.txt",
        "jmvx-leader-h2cs.txt",
        "bench-h2cs.txt", #has the timing data
        "jmvx-leader-jme.txt",
        "jmvx-leader-jython.txt",
        "jmvx-leader-luindex.txt",
        "jmvx-leader-lusearch.txt",
        "jmvx-leader-pmd.txt",
        "jmvx-leader-sunflow.txt",
        "jmvx-leader-xalan.txt",
        ]

#add files for leader/follower nosync
files.extend([f"jmvx-leader-nosync-{b}.txt" for b in mem.bms])
files.extend([f"jmvx-follower-nosync-{b}.txt" for b in mem.bms])

results = {}
superscript = {
    "avrora": "C",
    "batik": "C",
    "fop": "C",
    "h2": "B",
    "jython": "C",
    "luindex": "B",
    "lusearch": "B",
    "pmd": "C",
    "sunflow": "C",
    "xalan": "C",
    "jme": "C",
}

base_dir = sys.argv[1]
output   = sys.argv[2]
rm_under = 0

try:
    rm_under = int(sys.argv[3])
except ValueError:
    print("Invalid param for remove under: " + sys.argv[3])
except IndexError:
    pass

#compatibility with table_mem
mem.infolder = base_dir
#reuse the parsing code from table-mem
leader_mem = mem.read_benches("jmvx-leader")
leader_nosync_mem = mem.read_benches("jmvx-leader-nosync")
follower_mem = mem.read_benches("jmvx-follower")
follower_nosync_mem = mem.read_benches("jmvx-follower-nosync")
vanilla_mem = mem.read_benches("vanilla")

leader_over = mem.max_overhead(vanilla_mem, leader_mem)
leader_nosync_over = mem.max_overhead(vanilla_mem, leader_nosync_mem)
follower_over = mem.max_overhead(vanilla_mem, follower_mem)
follower_nosync_over = mem.max_overhead(vanilla_mem, follower_nosync_mem)

def convert_time(minute, sec):
    return ((60*int(minute)) + float(sec)) * 1000

for f in files:
    file = join(base_dir, f);
    if not exists(file):
        results[f] = np.array([np.nan])
        continue
    results[f] = np.array([])
    diverged = False
    with open(file, 'r') as fp:
        for line in fp:
            if("DivergenceError" in line):
                diverged = True

            result = re.match(".*PASSED in ([0-9]+) msec.*", line)
            if result is not None:
                if(not diverged):
                    results[f] = np.append(results[f], np.double(result.group(1)))
                diverged = False
            result = re.search(r"(\d+):(\d+\.?\d*)elapsed", line)
            if("h2cs" in f and result):
                if(not diverged):
                    results[f] = np.append(results[f], convert_time(result.group(1), result.group(2)))
                diverged = False
    #benchmark isn't reliable, cull it
    if(results[f].shape[0] < rm_under and not "vanilla" in file): # and not "nosync" in f):
        print(f"Warning, {file} contains less than {rm_under} completed runs", file=sys.stderr)
        #if(results[f].shape[0] < rm_under):
        results[f] = np.array([np.nan])#[0])

def printLineTxt(experiment,vanilla,leader,follower,los,fos):
    v  = np.average(results[vanilla])
    la = np.average(results[leader])
    fa = np.average(results[follower]) if results[follower].size > 0 else np.nan

    lo=la/v
    fo=fa/v

    ns_str = ((" " * 8) + "-----" + (" " * 8)) + "| ---- |"

    print("{:<10} | {:8.1f} +- {:<8.1f} |".format(experiment, v, np.std(results[vanilla])), end = " ")
    if(isnan(lo)):
        print(ns_str, end="")
    else:
        print("{:8.1f} +- {:<8.1f} | {:4.2f} |".format(la, np.std(results[leader]), lo), end = " ")
        los = np.append(los, lo)

    if(isnan(fo)):
        print(ns_str)
    else:
        print("{:8.1f} +- {:<8.1f} | {:4.2f}".format(fa, np.std(results[follower]), fo))
        fos = np.append(fos, fo)
    
    return (los,fos)

def printTxt():
    los=np.array([])
    fos=np.array([])
    print("{:<10} | {:20} | {:20} | {:4.3} | {:20} | {:4.3} ".format("Bench", "Vanilla", "Leader (Sync)", "AVG", "Follower (Sync)", "AVG"))
    (los,fos) = printLineTxt("avrora"  , "vanilla-avrora.txt"  , "jmvx-leader-avrora.txt"  , "jmvx-follower-avrora.txt"  , los, fos)
    (los,fos) = printLineTxt("batik"   , "vanilla-batik.txt"   , "jmvx-leader-batik.txt"   , "jmvx-follower-batik.txt"   , los, fos)
    (los,fos) = printLineTxt("fop"     , "vanilla-fop.txt"     , "jmvx-leader-fop.txt"     , "jmvx-follower-fop.txt"     , los, fos)
    (los,fos) = printLineTxt("h2"      , "vanilla-h2.txt"      , "jmvx-leader-h2.txt"      , "jmvx-follower-h2.txt"      , los, fos)
    (los,fos) = printLineTxt("h2 server"      , "vanilla-h2cs.txt"      , "jmvx-leader-h2cs.txt"      , "jmvx-follower-h2cs.txt"      , los, fos)
    (los,fos) = printLineTxt("jme   ", "vanilla-jme.txt"   , "jmvx-leader-jme.txt"   , "jmvx-follower-jme.txt"   , los, fos)
    (los,fos) = printLineTxt("jython"  , "vanilla-jython.txt"  , "jmvx-leader-jython.txt"  , "jmvx-follower-jython.txt"  , los, fos)
    (los,fos) = printLineTxt("luindex" , "vanilla-luindex.txt" , "jmvx-leader-luindex.txt" , "jmvx-follower-luindex.txt" , los, fos)
    (los,fos) = printLineTxt("lusearch", "vanilla-lusearch.txt", "jmvx-leader-lusearch.txt", "jmvx-follower-lusearch.txt", los, fos)
    (los,fos) = printLineTxt("pmd     ", "vanilla-pmd.txt"     , "jmvx-leader-pmd.txt"     , "jmvx-follower-pmd.txt"     , los, fos)
    (los,fos) = printLineTxt("sunflow ", "vanilla-sunflow.txt" , "jmvx-leader-sunflow.txt" , "jmvx-follower-sunflow.txt" , los, fos)
    (los,fos) = printLineTxt("xalan   ", "vanilla-xalan.txt"   , "jmvx-leader-xalan.txt"   , "jmvx-follower-xalan.txt"   , los, fos)
    los = los[np.isfinite(los)]
    fos = fos[np.isfinite(fos)]
    print("{:<10} | {:20} | {:20} | {:4.2f} | {:20} | {:4.2f} ".format("AVG", "", "", np.average(los), "", np.average(fos)))

l_ns_perf = np.array([])
f_ns_perf = np.array([])

def printLineLaTeX(experiment, vanilla, leader, follower,los,fos):
    global l_ns_perf
    global f_ns_perf
    v = np.average(results[vanilla])
    vd = np.std(results[vanilla])
    la = np.average(results[leader])
    ls = np.std(results[leader])
    fa = np.average(results[follower]) if results[follower].size > 0 else np.nan
    fs = np.std(results[follower]) if results[follower].size > 0 else np.nan

    lo=la/v
    fo=fa/v
    bench = experiment.strip() #key for memory data
    #deal with file shorthand
    if(bench == "h2 server"):
        bench = "h2cs"

    ss = ""
    try:
        ss = r"\textsuperscript{" + superscript[bench] + "}"
    except KeyError:
        pass

    l_nosync = results[f"jmvx-leader-nosync-{bench}.txt"]
    f_nosync = results[f"jmvx-follower-nosync-{bench}.txt"]
    l_ns = np.average(l_nosync) / v
    f_ns = np.average(f_nosync) / v
    
#   old printing method, in case a mistake is somewhere...
#    print( " & ~ & ~ & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $ & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $\\\\".format(
#        la, ls, lo,
#        fa, fs, fo
#        ))

    # name and vanilla time avg + stdev
    print(r"{:<25} & $ {:.1f} \pm {:.1f} $ &".format(experiment + ss,v,vd), end = "")

    # No sync leader perf and mem
    if(isnan(l_ns) or l_ns == 0):
        print("--- & --- &")
        try:
            del leader_nosync_over[bench]
        except KeyError:
            pass
    else:
        print("$ {:.2f} \\times $ & $ {:.2f} \\times $ &".format(l_ns, leader_nosync_over.get(bench,0)))
        l_ns_perf =np.append(l_ns_perf, l_ns)

    # Sync leader perf and mem
    if(isnan(lo)):
        print("--- & --- &")
        try:
            del leader_over[bench]
        except KeyError:
            pass
    else:
        print("$ {:.2f} \\times $ & $ {:.2f} \\times $ &".format(lo, leader_over.get(bench,0)))
        los = np.append(los,lo)

    # No sync follower perf and mem
    if(isnan(f_ns) or f_ns == 0):
        print("--- & --- &")
        try:
            del follower_nosync_over[bench]
        except KeyError:
            pass
    else:
        print("$ {:.2f} \\times $ & $ {:.2f} \\times $ &".format(f_ns, follower_nosync_over.get(bench, 0)))
        f_ns_perf = np.append(f_ns_perf, f_ns)

    # Sync follower perf and mem
    if(isnan(fo)):
        print("--- & ---\\\\")
        try:
            del follower_over[bench]
        except KeyError:
            pass
    else:
        print("$ {:.2f} \\times $ & $ {:.2f} \\times $\\\\".format(fo, follower_over.get(bench,0)))
        fos = np.append(fos,fo)

    print(r"\hline")

#    los = np.append(los,lo)
#    fos = np.append(fos,fo)

    return (los,fos)

def printLaTeX():
    print(r"""
    \begin{tabular}{| l | c | c | c | c | c | c | c | c | c | c |}
        \hline
        \multirow{3}{*}{\textbf{Program}} & \multirow{2}{*}{\textbf{Vanilla}} & \multicolumn{4}{c|}{\textbf{Leader}}  & \multicolumn{4}{c|}{\textbf{Follower}} \\
        \cline{3-10}
        & ~ & \multicolumn{2}{c|}{\textbf{No Sync}} & \multicolumn{2}{c|}{\textbf{Sync}} & \multicolumn{2}{c|}{\textbf{No Sync}} & \multicolumn{2}{c|}{\textbf{Sync}} \\
        \cline{2-10}
        & \emph{Time} & \emph{Perf} & \emph{Mem} & \emph{Perf} & \emph{Mem} & \emph{Perf} & \emph{Mem} & \emph{Perf} & \emph{Mem} \\
        """)

    los=np.array([])
    fos=np.array([])
    print(r"\hline")
    (los,fos) = printLineLaTeX("avrora"     , "vanilla-avrora.txt"  , "jmvx-leader-avrora.txt"  , "jmvx-follower-avrora.txt"  , los, fos)
    (los,fos) = printLineLaTeX("batik"      , "vanilla-batik.txt"   , "jmvx-leader-batik.txt"   , "jmvx-follower-batik.txt"   , los, fos)
    (los,fos) = printLineLaTeX("fop"        , "vanilla-fop.txt"     , "jmvx-leader-fop.txt"     , "jmvx-follower-fop.txt"     , los, fos)
    (los,fos) = printLineLaTeX("h2"         , "vanilla-h2.txt"      , "jmvx-leader-h2.txt"      , "jmvx-follower-h2.txt"      , los, fos)
    (los,fos) = printLineLaTeX("h2 server"  , "vanilla-h2cs.txt"    , "jmvx-leader-h2cs.txt"          , "jmvx-follower-h2cs.txt"    , los, fos)
    (los,fos) = printLineLaTeX("jme"        , "vanilla-jme.txt"   , "jmvx-leader-jme.txt"   , "jmvx-follower-jme.txt"   , los, fos)
    (los,fos) = printLineLaTeX("jython"     , "vanilla-jython.txt"  , "jmvx-leader-jython.txt"  , "jmvx-follower-jython.txt"  , los, fos)
    (los,fos) = printLineLaTeX("luindex"    , "vanilla-luindex.txt" , "jmvx-leader-luindex.txt" , "jmvx-follower-luindex.txt" , los, fos)
    (los,fos) = printLineLaTeX("lusearch"   , "vanilla-lusearch.txt", "jmvx-leader-lusearch.txt", "jmvx-follower-lusearch.txt", los, fos)
    (los,fos) = printLineLaTeX("pmd"        , "vanilla-pmd.txt"     , "jmvx-leader-pmd.txt"     , "jmvx-follower-pmd.txt"     , los, fos)
    (los,fos) = printLineLaTeX("sunflow"    , "vanilla-sunflow.txt" , "jmvx-leader-sunflow.txt" , "jmvx-follower-sunflow.txt" , los, fos)
    (los,fos) = printLineLaTeX("xalan"      , "vanilla-xalan.txt"   , "jmvx-leader-xalan.txt"   , "jmvx-follower-xalan.txt"   , los, fos)

    los = los[np.isfinite(los)]
    fos = fos[np.isfinite(fos)]
    print(r"\hline")
    print(r"\textbf{AVG}" + r" & --- & ${:.2f} \times$ & ${:.2f} \times$ &  ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ \\".format(
        np.average(l_ns_perf), mem.avg_maxes(leader_nosync_over), #leader nosync
        np.average(los), mem.avg_maxes(leader_over), #leader
        np.average(f_ns_perf), mem.avg_maxes(follower_nosync_over), #follower nosync
        np.average(fos), mem.avg_maxes(follower_over))) #follower
    print(r"\hline")

    print(r"\end{tabular}")

def printLineCsv(experiment,vanilla,leader,follower,los,fos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    la = np.average(results[leader])
    fa = np.average(results[follower]) if results[follower].size > 0 else np.nan

    if(v == 0):
        v = np.nan

    lo=la/v
    fo=fa/v

    print("{},{:.2f},{:.2f},".format(experiment,v,vs), end = "")
    if(isnan(lo)):
        print(",,,", end="")
    else:
        print("{:.1f},{:.1f},{:.2f},".format(la, np.std(results[leader]), lo), end = "")
        los = np.append(los, lo)

    if(isnan(fo)):
        print(",,")
    else:
        print("{:.1f},{:.1f},{:.2f}".format(fa, np.std(results[follower]), fo))
        fos = np.append(fos, fo)
    
    return (los,fos)

def printCsv():
    los=np.array([])
    fos=np.array([])
    print("experiment,vanilla,vanilla_std,leader,leader_std,leader_over,follower,follower_std,follower_over")
    (los,fos) = printLineCsv("avrora"  , "vanilla-avrora.txt"  , "jmvx-leader-avrora.txt"  , "jmvx-follower-avrora.txt"  , los, fos)
    (los,fos) = printLineCsv("batik"   , "vanilla-batik.txt"   , "jmvx-leader-batik.txt"   , "jmvx-follower-batik.txt"   , los, fos)
    (los,fos) = printLineCsv("fop"     , "vanilla-fop.txt"     , "jmvx-leader-fop.txt"     , "jmvx-follower-fop.txt"     , los, fos)
    (los,fos) = printLineCsv("h2"      , "vanilla-h2.txt"      , "jmvx-leader-h2.txt"      , "jmvx-follower-h2.txt"      , los, fos)
    (los,fos) = printLineCsv("h2 server"      , "vanilla-h2cs.txt"      , "jmvx-leader-h2cs.txt"      , "jmvx-follower-h2cs.txt"      , los, fos)
    (los,fos) = printLineCsv("jme", "vanilla-jme.txt"   , "jmvx-leader-jme.txt"   , "jmvx-follower-jme.txt"   , los, fos)
    (los,fos) = printLineCsv("jython"  , "vanilla-jython.txt"  , "jmvx-leader-jython.txt"  , "jmvx-follower-jython.txt"  , los, fos)
    (los,fos) = printLineCsv("luindex" , "vanilla-luindex.txt" , "jmvx-leader-luindex.txt" , "jmvx-follower-luindex.txt" , los, fos)
    (los,fos) = printLineCsv("lusearch", "vanilla-lusearch.txt", "jmvx-leader-lusearch.txt", "jmvx-follower-lusearch.txt", los, fos)
    (los,fos) = printLineCsv("pmd", "vanilla-pmd.txt"     , "jmvx-leader-pmd.txt"     , "jmvx-follower-pmd.txt"     , los, fos)
    (los,fos) = printLineCsv("sunflow", "vanilla-sunflow.txt" , "jmvx-leader-sunflow.txt" , "jmvx-follower-sunflow.txt" , los, fos)
    (los,fos) = printLineCsv("xalan", "vanilla-xalan.txt"   , "jmvx-leader-xalan.txt"   , "jmvx-follower-xalan.txt"   , los, fos)
    los = los[np.isfinite(los)]
    fos = fos[np.isfinite(fos)]
    print("{},,,,,{:.2f},,,{:.2f}".format("AVG", np.average(los), np.average(fos)))
    
if output == 'txt':
    printTxt()
if output == 'latex':
    printLaTeX()
if output == 'csv':
    printCsv()
