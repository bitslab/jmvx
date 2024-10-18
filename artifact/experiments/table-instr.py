#!/usr/bin/python3

import re
import numpy as np
import sys
from os.path import exists, join


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
        "jmvx-sync-avrora.txt",
        "jmvx-sync-batik.txt",
        "jmvx-sync-fop.txt",
        "jmvx-sync-h2.txt",
        "jmvx-sync-h2cs.txt",
        "jmvx-sync-jme.txt",
        "jmvx-sync-jython.txt",
        "jmvx-sync-luindex.txt",
        "jmvx-sync-lusearch.txt",
        "jmvx-sync-pmd.txt",
        "jmvx-sync-sunflow.txt",
        "jmvx-sync-xalan.txt",
        "jmvx-nosync-avrora.txt",
        "jmvx-nosync-batik.txt",
        "jmvx-nosync-fop.txt",
        "jmvx-nosync-h2.txt",
        "jmvx-nosync-h2cs.txt",
        "jmvx-nosync-jme.txt",
        "jmvx-nosync-jython.txt",
        "jmvx-nosync-luindex.txt",
        "jmvx-nosync-lusearch.txt",
        "jmvx-nosync-pmd.txt",
        "jmvx-nosync-sunflow.txt",
        "jmvx-nosync-xalan.txt",
        ]

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

results = {}

base_dir = sys.argv[1]
output   = sys.argv[2]
rm_under = 10

try:
    rm_under = int(sys.argv[3])
except:
    pass

def convert_time(minute, sec):
    return ((60*int(minute)) + float(sec)) * 1000

for f in files:
    file = join(base_dir, f);
    if not exists(file):
        results[f] = np.array([np.nan])
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
    if(results[f].shape[0] < rm_under):
        print(f"Warning, {file} contains less than {rm_under} completed runs", file=sys.stderr)
        results[f] = np.array([np.nan])

def printLineTxt(experiment,vanilla,nosync,sync,os,nos):
    v  = np.average(results[vanilla])
    s  = np.average(results[sync])
    ns = np.average(results[nosync])

    o=s/v
    no=ns/v

    print("{:<10} | {:8.1f} +- {:<8.1f} | {:8.1f} +- {:<8.1f} | {:3.2f} | {:8.1f} +- {:<8.1f} | {:3.2f} ".format(
        experiment,
        v, np.std(results[vanilla]),
        ns, np.std(results[nosync]), no,
        s, np.std(results[sync]), o
        ))

    os  = np.append(os,o)
    nos = np.append(nos,no)

    return (os,nos)

def printTxt():
    o=np.array([])
    no=np.array([])
    print("{:<10} | {:20} | {:20} | {:4.3} | {:20} | {:4.3} ".format("Bench", "Vanilla", "NoSync Passthrough", "AVG", "Sync Passthrough", "AVG"))
    (o,no) = printLineTxt("avrora"  , "vanilla-avrora.txt"  , "jmvx-nosync-avrora.txt"  , "jmvx-sync-avrora.txt"  , o, no)
    (o,no) = printLineTxt("batik"   , "vanilla-batik.txt"   , "jmvx-nosync-batik.txt"   , "jmvx-sync-batik.txt"   , o, no)
    (o,no) = printLineTxt("fop"     , "vanilla-fop.txt"     , "jmvx-nosync-fop.txt"     , "jmvx-sync-fop.txt"     , o, no)
    (o,no) = printLineTxt("h2"      , "vanilla-h2.txt"      , "jmvx-nosync-h2.txt"      , "jmvx-sync-h2.txt"      , o, no)
    (o,no) = printLineTxt("h2 server"      , "vanilla-h2cs.txt"      , "jmvx-nosync-h2cs.txt"      , "jmvx-sync-h2cs.txt"      , o, no)
    (o,no) = printLineTxt("jme   ", "vanilla-jme.txt"   , "jmvx-nosync-jme.txt"   , "jmvx-sync-jme.txt"   , o, no)
    (o,no) = printLineTxt("jython"  , "vanilla-jython.txt"  , "jmvx-nosync-jython.txt"  , "jmvx-sync-jython.txt"  , o, no)
    (o,no) = printLineTxt("luindex" , "vanilla-luindex.txt" , "jmvx-nosync-luindex.txt" , "jmvx-sync-luindex.txt" , o, no)
    (o,no) = printLineTxt("lusearch", "vanilla-lusearch.txt", "jmvx-nosync-lusearch.txt", "jmvx-sync-lusearch.txt", o, no)
    (o,no) = printLineTxt("pmd     ", "vanilla-pmd.txt"     , "jmvx-nosync-pmd.txt"     , "jmvx-sync-pmd.txt"     , o, no)
    (o,no) = printLineTxt("sunflow ", "vanilla-sunflow.txt" , "jmvx-nosync-sunflow.txt" , "jmvx-sync-sunflow.txt" , o, no)
    (o,no) = printLineTxt("xalan   ", "vanilla-xalan.txt"   , "jmvx-nosync-xalan.txt"   , "jmvx-sync-xalan.txt"   , o, no)
    o = o[np.isfinite(o)]
    no = no[np.isfinite(no)]
    print("{:<10} | {:<20} | {:<20} | {:.2f} | {:<20} | {:.2f} ".format("AVG", "", "", np.average(no), "", np.average(o)))

def printLineLaTeX(experiment, vanilla, nosync, sync,os,nos):
    v = np.average(results[vanilla])
    s = np.average(results[sync])
    ns = np.average(results[nosync])

    o=s/v
    no=ns/v

    ss = ""
    try:
        ss = r"\superscript{" + superscript[experiment] + "}"
    except KeyError:
        pass

    print(r"{:<25}".format(experiment+ss) +
          " & $ {:.1f} \pm {:.1f} $ & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $ & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $\\\\".format(
        v, np.std(results[vanilla]),
        ns, np.std(results[nosync]), no,
        s, np.std(results[sync]), o
        ))
    print(r"\hline")

    os  = np.append(os,o)
    nos = np.append(nos,no)

    return (os,nos)

def printLaTeX():
    print(r"""
    \begin{tabular}{| l | c | c | c | c | c |}
        \hline
        \multirow{2}{*}{\textbf{Program}} & \textbf{Vanilla} & \multicolumn{2}{c|}{\textbf{No Sync}} & \multicolumn{2}{c|}{\textbf{Sync}} \\
        \cline{2-6}
        & \emph{Time} & \emph{Time} & \emph{Overhead}  & \emph{Time} & \emph{Overhead} \\
        """)

    o=np.array([])
    no=np.array([])
    print(r"\hline")
    (o,no)=printLineLaTeX("avrora"    , "vanilla-avrora.txt"  , "jmvx-nosync-avrora.txt"  , "jmvx-sync-avrora.txt"  ,o,no)
    (o,no)=printLineLaTeX("batik"     , "vanilla-batik.txt"   , "jmvx-nosync-batik.txt"   , "jmvx-sync-batik.txt"   ,o,no)
    (o,no)=printLineLaTeX("fop"       , "vanilla-fop.txt"     , "jmvx-nosync-fop.txt"     , "jmvx-sync-fop.txt"     ,o,no)
    (o,no)=printLineLaTeX("h2"        , "vanilla-h2.txt"      , "jmvx-nosync-h2.txt"      , "jmvx-sync-h2.txt"      ,o,no)
    (o,no)=printLineLaTeX("h2 server" , "vanilla-h2cs.txt"      , "jmvx-nosync-h2cs.txt"      , "jmvx-sync-h2cs.txt"      , o, no)
    (o,no)=printLineLaTeX("jme"     , "vanilla-jme.txt"   , "jmvx-nosync-jme.txt"   , "jmvx-sync-jme.txt"   ,o,no)
    (o,no)=printLineLaTeX("jython"    , "vanilla-jython.txt"  , "jmvx-nosync-jython.txt"  , "jmvx-sync-jython.txt"  ,o,no)
    (o,no)=printLineLaTeX("luindex"   , "vanilla-luindex.txt" , "jmvx-nosync-luindex.txt" , "jmvx-sync-luindex.txt" ,o,no)
    (o,no)=printLineLaTeX("lusearch"  , "vanilla-lusearch.txt", "jmvx-nosync-lusearch.txt", "jmvx-sync-lusearch.txt",o,no)
    (o,no)=printLineLaTeX("pmd"       , "vanilla-pmd.txt"     , "jmvx-nosync-pmd.txt"     , "jmvx-sync-pmd.txt"     ,o,no)
    (o,no)=printLineLaTeX("sunflow"   , "vanilla-sunflow.txt" , "jmvx-nosync-sunflow.txt" , "jmvx-sync-sunflow.txt" ,o,no)
    (o,no)=printLineLaTeX("xalan"     , "vanilla-xalan.txt"   , "jmvx-nosync-xalan.txt"   , "jmvx-sync-xalan.txt"   ,o,no)
    o = o[np.isfinite(o)]
    no = no[np.isfinite(no)]
    print(r"\hline")
    print(r"\textbf{AVG}" + r" & --- & --- & ${:.2f} \times$ & --- & ${:.2f} \times$ \\".format(np.average(no),np.average(o)))
    print(r"\hline")

    print(r"\end{tabular}")

def printLineCsv(experiment,vanilla,nosync,sync,os,nos):
    v  = np.average(results[vanilla])
    s  = np.average(results[sync])
    ns = np.average(results[nosync])

    o=s/v
    no=ns/v

    print("{},{:.1f},{:.1f},{:.1f},{:.1f},{:.2f},{:.1f},{:.1f},{:.2f}".format(
        experiment,
        v, np.std(results[vanilla]),
        ns, np.std(results[nosync]), no,
        s, np.std(results[sync]), o
        ))

    os  = np.append(os,o)
    nos = np.append(nos,no)

    return (os,nos)

def printCsv():
    o=np.array([])
    no=np.array([])
    print("experiment,vanilla,vanilla_std,nosync,nosync_std,nosync_over,sync,sync_std,sync_over")
    (o,no) = printLineCsv("avrora"  , "vanilla-avrora.txt"  , "jmvx-nosync-avrora.txt"  , "jmvx-sync-avrora.txt"  , o, no)
    (o,no) = printLineCsv("batik"   , "vanilla-batik.txt"   , "jmvx-nosync-batik.txt"   , "jmvx-sync-batik.txt"   , o, no)
    (o,no) = printLineCsv("fop"     , "vanilla-fop.txt"     , "jmvx-nosync-fop.txt"     , "jmvx-sync-fop.txt"     , o, no)
    (o,no) = printLineCsv("h2"      , "vanilla-h2.txt"      , "jmvx-nosync-h2.txt"      , "jmvx-sync-h2.txt"      , o, no)
    (o,no) = printLineCsv("h2 server"      , "vanilla-h2cs.txt"      , "jmvx-nosync-h2cs.txt"      , "jmvx-sync-h2cs.txt"      , o, no)
    (o,no) = printLineCsv("jme", "vanilla-jme.txt"   , "jmvx-nosync-jme.txt"   , "jmvx-sync-jme.txt"   , o, no)
    (o,no) = printLineCsv("jython"  , "vanilla-jython.txt"  , "jmvx-nosync-jython.txt"  , "jmvx-sync-jython.txt"  , o, no)
    (o,no) = printLineCsv("luindex" , "vanilla-luindex.txt" , "jmvx-nosync-luindex.txt" , "jmvx-sync-luindex.txt" , o, no)
    (o,no) = printLineCsv("lusearch", "vanilla-lusearch.txt", "jmvx-nosync-lusearch.txt", "jmvx-sync-lusearch.txt", o, no)
    (o,no) = printLineCsv("pmd", "vanilla-pmd.txt"     , "jmvx-nosync-pmd.txt"     , "jmvx-sync-pmd.txt"     , o, no)
    (o,no) = printLineCsv("sunflow", "vanilla-sunflow.txt" , "jmvx-nosync-sunflow.txt" , "jmvx-sync-sunflow.txt" , o, no)
    (o,no) = printLineCsv("xalan", "vanilla-xalan.txt"   , "jmvx-nosync-xalan.txt"   , "jmvx-sync-xalan.txt"   , o, no)
    o = o[np.isfinite(o)]
    no = no[np.isfinite(no)]
    print("{},,,,,{:.2f},,,{:.2f}".format("AVG", np.average(no), np.average(o)))

if output == 'txt':
    printTxt()
if output == 'latex':
    printLaTeX()
if output == 'csv':
    printCsv()
