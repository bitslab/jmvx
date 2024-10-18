#!/usr/bin/python3

import re
import numpy as np
import sys
from os.path import exists, join
from math import isnan


files = [
        "vanilla-rec-avrora.txt",
        "vanilla-rec-batik.txt",
        "vanilla-rec-fop.txt",
        "vanilla-rec-h2.txt",
        "vanilla-rec-h2cs.txt",
        "vanilla-rec-jme.txt",
        "vanilla-rec-jython.txt",
        "vanilla-rec-luindex.txt",
        "vanilla-rec-lusearch.txt",
        "vanilla-rec-pmd.txt",
        "vanilla-rec-sunflow.txt",
        "vanilla-rec-xalan.txt",
        "jmvx-rep-avrora.txt",
        "jmvx-rep-batik.txt",
        "jmvx-rep-fop.txt",
        "jmvx-rep-h2.txt",
        "jmvx-rep-h2cs.txt",
        "jmvx-rep-jme.txt",
        "jmvx-rep-jython.txt",
        "jmvx-rep-luindex.txt",
        "jmvx-rep-lusearch.txt",
        "jmvx-rep-pmd.txt",
        "jmvx-rep-sunflow.txt",
        "jmvx-rep-xalan.txt",
        "jmvx-rep-nosync-avrora.txt",
        "jmvx-rep-nosync-batik.txt",
        "jmvx-rep-nosync-fop.txt",
        "jmvx-rep-nosync-h2.txt",
        "jmvx-rep-nosync-h2cs.txt",
        "jmvx-rep-nosync-jme.txt",
        "jmvx-rep-nosync-jython.txt",
        "jmvx-rep-nosync-luindex.txt",
        "jmvx-rep-nosync-lusearch.txt",
        "jmvx-rep-nosync-pmd.txt",
        "jmvx-rep-nosync-sunflow.txt",
        "jmvx-rep-nosync-xalan.txt",
        ]

results = {}
sizes = {}

base_dir = sys.argv[1]
output   = sys.argv[2]
rm_under = 0

try:
    rm_under = int(sys.argv[3])
except ValueError:
    print("Invalid param for remove under: " + sys.argv[3])
except IndexError:
    pass

def convert_time(minute, sec):
    return ((60*int(minute)) + float(sec)) * 1000

for f in files:
    file = join(base_dir, f);
    if not exists(file):
        results[f] = np.array([np.nan])
        sizes[f] = ""
        continue
    results[f] = np.array([])
    with open(file, 'r') as fp:
        for line in fp:
            result = re.match(".*PASSED in ([0-9]+) msec.*", line)
            if result is not None:
                results[f] = np.append(results[f], np.double(result.group(1)))
            result = re.search(r"(\d+):(\d+\.?\d*)elapsed", line)
            if("h2cs" in f and result):
                results[f] = np.append(results[f], convert_time(result.group(1), result.group(2)))
            result = re.match("total ([0-9]+(\.[0-9]+)?.)", line)
            if result is not None:
                sizes[f] = result.group(1)
            #result = re.match("Exception", line)
            #if result is not None and "nosync" in f:
            #    results[f] = None
            #    break
    #benchmark isn't reliable, cull it
    if(results[f].shape[0] < rm_under and not "vanilla" in file):
        print(f"Warning, {file} contains less than 10 completed runs", file=sys.stderr)
        results[f] = np.array([np.nan])

def printLineTxt(experiment,vanilla,sync,nosync,os,nos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    ns_str = "         ---         | --- "
    if isnan(fa):
        fa = 0
        fs = 0
        print("{:<10} | {:8.1f} +- {:8.1f} | {} | {}".format(experiment, v, vs, ns_str, ns_str))
        return (os, nos)

    if results[nosync] is not None and results[nosync].size > 0:
        na = np.average(results[nosync])
        ns = np.std(results[nosync])
        if isnan(na):
            na = 0
            ns = 0
            no = 0
        else:
            no=na/v
            nos = np.append(nos,no)
            ns_str = "{:8.1f} +- {:8.1f} | {:.2f}".format(na, ns, no)
    else:
        na = 0
        ns = 0
        no = 0

    o=fa/v

    print("{:<10} | {:8.1f} +- {:8.1f} | {} | {:8.1f} +- {:8.1f} | {:.2f}  ".format(
        experiment,
        v, vs,
        ns_str,
        fa, fs, o))

    os = np.append(os,o)

    return (os,nos)

def printTxt():
    os=np.array([])
    nos=np.array([])
    print("{:<10} | {:20} | {:20} | {:4.3} | {:20} | {:4.3} ".format("Bench", "Vanilla", "NoSync Replay", "AVG", "Sync Replay", "AVG"))
    (os,nos) = printLineTxt("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rep-avrora.txt"  , "jmvx-rep-nosync-avrora.txt"  , os, nos)
    (os,nos) = printLineTxt("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rep-batik.txt"   , "jmvx-rep-nosync-batik.txt"   , os, nos)
    (os,nos) = printLineTxt("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rep-fop.txt"     , "jmvx-rep-nosync-fop.txt"     , os, nos)
    (os,nos) = printLineTxt("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rep-h2.txt"      , "jmvx-rep-nosync-h2.txt"      , os, nos)
    (os,nos) = printLineTxt("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rep-h2cs.txt"      , "jmvx-rep-nosync-h2cs.txt"      , os, nos)
    (os,nos) = printLineTxt("jme"  , "vanilla-rec-jme.txt"  , "jmvx-rep-jme.txt"  , "jmvx-rep-nosync-jme.txt"  , os, nos)
    (os,nos) = printLineTxt("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rep-jython.txt"  , "jmvx-rep-nosync-jython.txt"  , os, nos)
    (os,nos) = printLineTxt("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rep-luindex.txt" , "jmvx-rep-nosync-luindex.txt" , os, nos)
    (os,nos) = printLineTxt("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rep-lusearch.txt", "jmvx-rep-nosync-lusearch.txt", os, nos)
    (os,nos) = printLineTxt("pmd     ", "vanilla-rec-pmd.txt"     , "jmvx-rep-pmd.txt"     , "jmvx-rep-nosync-pmd.txt"     , os, nos)
    (os,nos) = printLineTxt("sunflow ", "vanilla-rec-sunflow.txt" , "jmvx-rep-sunflow.txt" , "jmvx-rep-nosync-sunflow.txt" , os, nos)
    (os,nos) = printLineTxt("xalan   ", "vanilla-rec-xalan.txt"   , "jmvx-rep-xalan.txt"   , "jmvx-rep-nosync-xalan.txt"   , os, nos)
    os = os[np.isfinite(os)] #drop an nan's that made it through
    nos = nos[np.isfinite(nos)]
    print("{:<10} | {:20} | {:20} | {:4.2f} | {:20} | {:4.2f} ".format("AVG", "", "", np.average(nos), "", np.average(os)))
#    print("{:<10} |                 |  {:.2f} |                  |  {:.2f} |".format("AVG", np.average(nos), np.average(os)))

def printLineLaTeX(experiment, vanilla, sync, nosync, os, nos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    if isnan(fa):
        fa = 0
        fs = 0

    ns_str = " --- & --- "
    if results[nosync] is not None and results[nosync].size > 0:
        na = np.average(results[nosync])
        ns = np.std(results[nosync])
        if isnan(na):
            na = 0
            ns = 0
            no = 0
        else:
            no=na/v
            nos = np.append(nos,no)
            ns_str = "$ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $".format(na, ns, no)
    else:
        na = 0
        ns = 0
        no = 0


    o=fa/v

    print(r"{:<10}".format(experiment) +
          " &  {} & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $ \\\\".format(
        ns_str,
        fa, fs, o
        ))
    print(r"\hline")

    os = np.append(os,o)

    return (os,nos)

def printLaTeX():
    print(r"""
    \begin{tabular}{| l | c | c | c | c |}
        \hline
        \multirow{2}{*}{\textbf{Program}} & \multicolumn{2}{c|}{\textbf{Replay --- No Sync}} & \multicolumn{2}{c|}{\textbf{Replay --- Sync}} \\
        \cline{2-5}
        & \emph{Time} & \emph{Overhead} & \emph{Time} & \emph{Overhead} \\
        
        """)

    os=np.array([])
    nos=np.array([])
    print(r"\hline")
    (os,nos) = printLineLaTeX("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rep-avrora.txt"  , "jmvx-rep-nosync-avrora.txt"  , os, nos)
    (os,nos) = printLineLaTeX("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rep-batik.txt"   , "jmvx-rep-nosync-batik.txt"   , os, nos)
    (os,nos) = printLineLaTeX("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rep-fop.txt"     , "jmvx-rep-nosync-fop.txt"     , os, nos)
    (os,nos) = printLineLaTeX("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rep-h2.txt"      , "jmvx-rep-nosync-h2.txt"      , os, nos)
    (os,nos) = printLineLaTeX("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rep-h2cs.txt"      , "jmvx-rep-nosync-h2cs.txt"      , os, nos)
    (os,nos) = printLineLaTeX("jme"  , "vanilla-rec-jme.txt"  , "jmvx-rep-jme.txt"  , "jmvx-rep-nosync-jme.txt"  , os, nos)
    (os,nos) = printLineLaTeX("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rep-jython.txt"  , "jmvx-rep-nosync-jython.txt"  , os, nos)
    (os,nos) = printLineLaTeX("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rep-luindex.txt" , "jmvx-rep-nosync-luindex.txt" , os, nos)
    (os,nos) = printLineLaTeX("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rep-lusearch.txt", "jmvx-rep-nosync-lusearch.txt", os, nos)
    (os,nos) = printLineLaTeX("pmd     ", "vanilla-rec-pmd.txt"     , "jmvx-rep-pmd.txt"     , "jmvx-rep-nosync-pmd.txt"     , os, nos)
    (os,nos) = printLineLaTeX("sunflow ", "vanilla-rec-sunflow.txt" , "jmvx-rep-sunflow.txt" , "jmvx-rep-nosync-sunflow.txt" , os, nos)
    (os,nos) = printLineLaTeX("xalan   ", "vanilla-rec-xalan.txt"   , "jmvx-rep-xalan.txt"   , "jmvx-rep-nosync-xalan.txt"   , os, nos)

    os = os[np.isfinite(os)] #drop an nan's that made it through
    nos = nos[np.isfinite(nos)]
    print(r"\hline")
    print(r"\textbf{AVG}" + r" & --- & ${:.2f} \times$ & --- & ${:.2f} \times$ \\".format(np.average(nos),np.average(os)))
    print(r"\hline")

    print(r"\end{tabular}")

def printLineCsv(experiment,vanilla,sync,nosync,os,nos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    fa = np.average(results[sync])
    fs = np.std(results[sync])
    o = 0

    if(v == 0):
        v = np.nan

    print("{},{:.2f},{:.2f},".format(experiment,v,vs), end="")

    na = 0
    ns = 0
    no = 0
    if results[nosync] is not None and results[nosync].size > 0:
        na = np.average(results[nosync])
        ns = np.std(results[nosync])
        if isnan(na):
            na = 0
            ns = 0
            no = 0
        else:
            no=na/v
            if(v > 0):
                nos = np.append(nos,no)
    else:
        na = 0
        ns = 0
        no = 0

    print("{:.1f},{:.1f},{:.2f},".format(na, ns, no), end="")

    if isnan(fa):
        fa = 0
        fs = 0
        o = 0
    else:
        o=fa/v
        if(o > 0):
            os = np.append(os, o)

    print("{:.2f},{:.2f},{:.2f}".format(fa, fs, o))

    return (os,nos)

def printCsv():
    os=np.array([])
    nos=np.array([])
    print("experiment,vanilla,vanilla_std,nosync,nosync_std,nosync_over,sync,sync_std,sync_over")
    (os,nos) = printLineCsv("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rep-avrora.txt"  , "jmvx-rep-nosync-avrora.txt"  , os, nos)
    (os,nos) = printLineCsv("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rep-batik.txt"   , "jmvx-rep-nosync-batik.txt"   , os, nos)
    (os,nos) = printLineCsv("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rep-fop.txt"     , "jmvx-rep-nosync-fop.txt"     , os, nos)
    (os,nos) = printLineCsv("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rep-h2.txt"      , "jmvx-rep-nosync-h2.txt"      , os, nos)
    (os,nos) = printLineCsv("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rep-h2cs.txt"      , "jmvx-rep-nosync-h2cs.txt"      , os, nos)
    (os,nos) = printLineCsv("jme"  , "vanilla-rec-jme.txt"  , "jmvx-rep-jme.txt"  , "jmvx-rep-nosync-jme.txt"  , os, nos)
    (os,nos) = printLineCsv("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rep-jython.txt"  , "jmvx-rep-nosync-jython.txt"  , os, nos)
    (os,nos) = printLineCsv("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rep-luindex.txt" , "jmvx-rep-nosync-luindex.txt" , os, nos)
    (os,nos) = printLineCsv("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rep-lusearch.txt", "jmvx-rep-nosync-lusearch.txt", os, nos)
    (os,nos) = printLineCsv("pmd", "vanilla-rec-pmd.txt"     , "jmvx-rep-pmd.txt"     , "jmvx-rep-nosync-pmd.txt"     , os, nos)
    (os,nos) = printLineCsv("sunflow", "vanilla-rec-sunflow.txt" , "jmvx-rep-sunflow.txt" , "jmvx-rep-nosync-sunflow.txt" , os, nos)
    (os,nos) = printLineCsv("xalan", "vanilla-rec-xalan.txt"   , "jmvx-rep-xalan.txt"   , "jmvx-rep-nosync-xalan.txt"   , os, nos)
    os = os[np.isfinite(os)] #drop an nan's that made it through
    nos = nos[np.isfinite(nos)]
    print("{},,,,,{:.2f},,,{:.2f}".format("AVG", np.average(nos), np.average(os)))

if output == 'txt':
    printTxt()
if output == 'latex':
    printLaTeX()
if output == 'csv':
    printCsv()

