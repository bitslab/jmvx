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
        "jmvx-rec-avrora.txt",
        "jmvx-rec-batik.txt",
        "jmvx-rec-fop.txt",
        "jmvx-rec-h2.txt",
        "jmvx-rec-h2cs.txt",
        "jmvx-rec-jme.txt",
        "jmvx-rec-jython.txt",
        "jmvx-rec-luindex.txt",
        "jmvx-rec-lusearch.txt",
        "jmvx-rec-pmd.txt",
        "jmvx-rec-sunflow.txt",
        "jmvx-rec-xalan.txt",
        "jmvx-rec-nosync-avrora.txt",
        "jmvx-rec-nosync-batik.txt",
        "jmvx-rec-nosync-fop.txt",
        "jmvx-rec-nosync-h2.txt",
        "jmvx-rec-nosync-h2cs.txt",
        "jmvx-rec-nosync-jme.txt",
        "jmvx-rec-nosync-jython.txt",
        "jmvx-rec-nosync-luindex.txt",
        "jmvx-rec-nosync-lusearch.txt",
        "jmvx-rec-nosync-pmd.txt",
        "jmvx-rec-nosync-sunflow.txt",
        "jmvx-rec-nosync-xalan.txt",
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
    #benchmark isn't reliable, cull it
    if(results[f].shape[0] < rm_under and not "vanilla" in file):
        print(f"Warning, {file} contains less than 10 completed runs", file=sys.stderr)
        results[f] = np.array([np.nan])

chronicler = {}
chronicler["avrora"  ] = 1.01
chronicler["batik"   ] = 1.08
chronicler["fop"     ] = 1.36
chronicler["h2"      ] = 1.06
chronicler["jython"  ] = 1.12
chronicler["luindex" ] = 1.01
chronicler["lusearch"] = 1.39
chronicler["pmd     "] = 1.11
chronicler["sunflow "] = 1.01
chronicler["xalan   "] = 1.21

chronicler_a = np.average(
[ chronicler["avrora"  ] ,
 chronicler["batik"   ] ,
 chronicler["fop"     ] ,
 chronicler["h2"      ] ,
 chronicler["jython"  ] ,
 chronicler["luindex" ] ,
 chronicler["lusearch"] ,
 chronicler["pmd     "] ,
 chronicler["sunflow "]  ,
 chronicler["xalan   "]  ])



def printLineTxt(experiment,vanilla,nosync,sync,los,fos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    la = np.average(results[nosync])
    ls = np.std(results[nosync])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    if isnan(la):
        la = 0
        ls = 0

    if isnan(fa):
        fa = 0
        fs = 0

    lo=la/v
    fo=fa/v

    print("{:<10} | {:6.1f} +- {:5.1f} | {:6.1f} +- {:5.1f} |  {:.2f} | {:<5} |".format(
        experiment,
        v, vs,
        la, ls, lo, sizes[nosync] 
        ), end= " ")
    try:
        print("{:.2f}".format(chronicler[experiment]), end = " | ") 
    except KeyError:
        print("---", end=" | ")

    print("{:7.1f} +- {:5.1f} |  {:.2f} | {:<5} ".format(fa, fs, fo, sizes[sync]))
    los = np.append(los,lo)
    fos = np.append(fos,fo)

    return (los,fos)

def printTxt():
    los=np.array([])
    fos=np.array([])
    (los,fos) = printLineTxt("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rec-nosync-avrora.txt"  , "jmvx-rec-avrora.txt"  , los, fos)
    (los,fos) = printLineTxt("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rec-nosync-batik.txt"   , "jmvx-rec-batik.txt"   , los, fos)
    (los,fos) = printLineTxt("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rec-nosync-fop.txt"     , "jmvx-rec-fop.txt"     , los, fos)
    (los,fos) = printLineTxt("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rec-nosync-h2.txt"      , "jmvx-rec-h2.txt"      , los, fos)
    (los,fos) = printLineTxt("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rec-nosync-h2cs.txt"      , "jmvx-rec-h2cs.txt"      , los, fos)
    (los,fos) = printLineTxt("jme"  , "vanilla-rec-jme.txt"  , "jmvx-rec-nosync-jme.txt"  , "jmvx-rec-jme.txt"  , los, fos)
    (los,fos) = printLineTxt("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rec-nosync-jython.txt"  , "jmvx-rec-jython.txt"  , los, fos)
    (los,fos) = printLineTxt("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rec-nosync-luindex.txt" , "jmvx-rec-luindex.txt" , los, fos)
    (los,fos) = printLineTxt("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rec-nosync-lusearch.txt", "jmvx-rec-lusearch.txt", los, fos)
    (los,fos) = printLineTxt("pmd     ", "vanilla-rec-pmd.txt"     , "jmvx-rec-nosync-pmd.txt"     , "jmvx-rec-pmd.txt"     , los, fos)
    (los,fos) = printLineTxt("sunflow ", "vanilla-rec-sunflow.txt" , "jmvx-rec-nosync-sunflow.txt" , "jmvx-rec-sunflow.txt" , los, fos)
    (los,fos) = printLineTxt("xalan   ", "vanilla-rec-xalan.txt"   , "jmvx-rec-nosync-xalan.txt"   , "jmvx-rec-xalan.txt"   , los, fos)
    print("{:<10} |                 |                 |  {:.2f} |       | {:.2f} |                  |  {:.2f} |".format("AVG", np.average(los), chronicler_a, np.average(fos)))

def printLineLaTeX(experiment, vanilla, nosync, sync,los,fos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    la = np.average(results[nosync])
    ls = np.std(results[nosync])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    if isnan(la):
        la = 0
        ls = 0

    if isnan(fa):
        fa = 0
        fs = 0


    lo=la/v
    fo=fa/v

    print(r"{:<10}".format(experiment) +
          " & $ {:.1f} \pm {:.1f} $ & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $ & {:<5} &".format(
        v, vs,
        la, ls, lo, sizes[nosync]
        ), end=" ") 
    try:
        print("$ {:.2f} \\times $ &".format(chronicler[experiment]), end=" ")
    except KeyError:
        print("--- &", end=" ")

    print("$ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $ & {:<5} \\\\".format(fa, fs, fo, sizes[sync]))
    print(r"\hline")

    los = np.append(los,lo)
    fos = np.append(fos,fo)

    return (los,fos)

def printLaTeX():
    print(r"""
    \begin{tabular}{| l | c | c | c | c | c | c | c | c | c |}
        \hline
        \multirow{2}{*}{\textbf{Program}} & \textbf{Vanilla} &
        \multicolumn{3}{c|}{\textbf{Record --- No Sync}} & \cite{chroniclerj} & \multicolumn{3}{c|}{\textbf{Record --- Sync}} \\
        \cline{3-9}
        & \emph{Time} & \emph{Time} & \emph{O/E} & \emph{Size} & \emph{Time}  & \emph{Time} & \emph{O/E} & \emph{Size} \\
        
        """)

    los=np.array([])
    fos=np.array([])
    print(r"\hline")
    (los,fos) = printLineLaTeX("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rec-nosync-avrora.txt"  , "jmvx-rec-avrora.txt"  , los, fos)
    (los,fos) = printLineLaTeX("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rec-nosync-batik.txt"   , "jmvx-rec-batik.txt"   , los, fos)
    (los,fos) = printLineLaTeX("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rec-nosync-fop.txt"     , "jmvx-rec-fop.txt"     , los, fos)
    (los,fos) = printLineLaTeX("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rec-nosync-h2.txt"      , "jmvx-rec-h2.txt"      , los, fos)
    (los,fos) = printLineLaTeX("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rec-nosync-h2cs.txt"      , "jmvx-rec-h2cs.txt"      , los, fos)
    (los,fos) = printLineLaTeX("jme"  , "vanilla-rec-jme.txt"  , "jmvx-rec-nosync-jme.txt"  , "jmvx-rec-jme.txt"  , los, fos)
    (los,fos) = printLineLaTeX("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rec-nosync-jython.txt"  , "jmvx-rec-jython.txt"  , los, fos)
    (los,fos) = printLineLaTeX("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rec-nosync-luindex.txt" , "jmvx-rec-luindex.txt" , los, fos)
    (los,fos) = printLineLaTeX("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rec-nosync-lusearch.txt", "jmvx-rec-lusearch.txt", los, fos)
    (los,fos) = printLineLaTeX("pmd     ", "vanilla-rec-pmd.txt"     , "jmvx-rec-nosync-pmd.txt"     , "jmvx-rec-pmd.txt"     , los, fos)
    (los,fos) = printLineLaTeX("sunflow ", "vanilla-rec-sunflow.txt" , "jmvx-rec-nosync-sunflow.txt" , "jmvx-rec-sunflow.txt" , los, fos)
    (los,fos) = printLineLaTeX("xalan   ", "vanilla-rec-xalan.txt"   , "jmvx-rec-nosync-xalan.txt"   , "jmvx-rec-xalan.txt"   , los, fos)

    print(r"\hline")
    print(r"\textbf{AVG}" + r" & --- & --- & ${:.2f} \times$ & --- &  ${:.2f} \times$ & --- & ${:.2f} \times$ & --- \\".format(np.average(los),chronicler_a,np.average(fos)))
    print(r"\hline")

    print(r"\end{tabular}")

def printLineCsv(experiment,vanilla,nosync,sync,los,fos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    la = np.average(results[nosync])
    ls = np.std(results[nosync])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    if(v == 0):
        v = np.nan

    if isnan(la):
        la = 0
        ls = 0

    if isnan(fa):
        fa = 0
        fs = 0

    lo=la/v
    fo=fa/v

    print("{},{:.1f},{:.1f},{:.1f},{:.1f},{:.2f},{},".format(
        experiment,
        v, vs,
        la, ls, lo, sizes[nosync] 
        ), end= "")
    try:
        print("{:.2f},".format(chronicler[experiment]), end = "") 
    except KeyError:
        print(",", end="")

    print("{:.1f},{:.1f},{:.2f},{}".format(fa, fs, fo, sizes[sync]))

    if(not isnan(lo)):
        los = np.append(los,lo)
    if(not isnan(fo)):
        fos = np.append(fos,fo)

    return (los,fos)

def printCsv():
    los=np.array([])
    fos=np.array([])
    print("experiment,vanilla,vanilla_std,nosync,nosync_std,nosync_over,nosync_size,chronicler,sync,sync_std,sync_over,sync_size")
    (los,fos) = printLineCsv("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rec-nosync-avrora.txt"  , "jmvx-rec-avrora.txt"  , los, fos)
    (los,fos) = printLineCsv("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rec-nosync-batik.txt"   , "jmvx-rec-batik.txt"   , los, fos)
    (los,fos) = printLineCsv("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rec-nosync-fop.txt"     , "jmvx-rec-fop.txt"     , los, fos)
    (los,fos) = printLineCsv("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rec-nosync-h2.txt"      , "jmvx-rec-h2.txt"      , los, fos)
    (los,fos) = printLineCsv("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rec-nosync-h2cs.txt"      , "jmvx-rec-h2cs.txt"      , los, fos)
    (los,fos) = printLineCsv("jme"  , "vanilla-rec-jme.txt"  , "jmvx-rec-nosync-jme.txt"  , "jmvx-rec-jme.txt"  , los, fos)
    (los,fos) = printLineCsv("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rec-nosync-jython.txt"  , "jmvx-rec-jython.txt"  , los, fos)
    (los,fos) = printLineCsv("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rec-nosync-luindex.txt" , "jmvx-rec-luindex.txt" , los, fos)
    (los,fos) = printLineCsv("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rec-nosync-lusearch.txt", "jmvx-rec-lusearch.txt", los, fos)
    (los,fos) = printLineCsv("pmd", "vanilla-rec-pmd.txt"     , "jmvx-rec-nosync-pmd.txt"     , "jmvx-rec-pmd.txt"     , los, fos)
    (los,fos) = printLineCsv("sunflow", "vanilla-rec-sunflow.txt" , "jmvx-rec-nosync-sunflow.txt" , "jmvx-rec-sunflow.txt" , los, fos)
    (los,fos) = printLineCsv("xalan", "vanilla-rec-xalan.txt"   , "jmvx-rec-nosync-xalan.txt"   , "jmvx-rec-xalan.txt"   , los, fos)
    print("{},,,,,{:.2f},,{:.2f},,,{:.2f},".format("AVG", np.average(los), chronicler_a, np.average(fos)))

if output == 'txt':
    printTxt()
if output == 'latex':
    printLaTeX()
if output == 'csv':
    printCsv()
