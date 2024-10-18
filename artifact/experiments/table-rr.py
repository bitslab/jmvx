#!/usr/bin/python3

import re
import numpy as np
import sys
from os.path import exists, join
from math import isnan
import table_mem as mem


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
        "jmvx-rec-nosync-jme.txt",
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

#add files for leader/follower nosync
#files.extend([f"jmvx-leader-nosync-{b}.txt" for b in mem.bms])
#files.extend([f"jmvx-follower-nosync-{b}.txt" for b in mem.bms])

results = {}
sizes = {}
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
rec_mem = mem.read_benches("jmvx-rec")

rec_nosync_mem = mem.read_benches("jmvx-rec-nosync")
rep_mem = mem.read_benches("jmvx-rep")
rep_nosync_mem = mem.read_benches("jmvx-rep-nosync")
#vanilla-rec doesn't have mem data in it
#sweep for vanilla mem was done later
vanilla_mem = mem.read_benches("vanilla-rec")

rec_over = mem.max_overhead(vanilla_mem, rec_mem)
rec_nosync_over = mem.max_overhead(vanilla_mem, rec_nosync_mem)
rep_over = mem.max_overhead(vanilla_mem, rep_mem)
rep_nosync_over = mem.max_overhead(vanilla_mem, rep_nosync_mem)

def convert_time(minute, sec):
    return ((60*int(minute)) + float(sec)) * 1000

for f in files:
    file = join(base_dir, f);
    if not exists(file):
        results[f] = np.array([np.nan]) 
        sizes[f] = 0
        continue
    results[f] = np.array([])
    diverged = False
    with open(file, 'r') as fp:
        for line in fp:
            if("DivergenceError" in line):
                diverged = True

            result = re.match(".*PASSED in ([0-9]+) msec.*", line)
            if result is not None:
                if(diverged):
                    diverged = False
                else:
                    results[f] = np.append(results[f], np.double(result.group(1)))

            result = re.search(r"(\d+):(\d+\.?\d*)elapsed", line)
            if("h2cs" in f and result):
                if(diverged):
                    diverged = False
                else:
                    results[f] = np.append(results[f], convert_time(result.group(1), result.group(2)))

            result = re.match("total ([0-9]+(\.[0-9]+)?.)", line)
            if result is not None:
                if(not diverged):
                    sizes[f] = result.group(1)
                diverged = False #reset for next iter

    #benchmark isn't reliable, cull it. 
    if(results[f].shape[0] < rm_under and not "vanilla" in file):
        print(f"Warning, {file} contains less than {rm_under} completed runs", file=sys.stderr)
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
    lo = 0
    fo = 0

    print("{:<10} | {:8.1f} +- {:8.1f} |".format(
        experiment,
        v, vs), end = " ")
    
    if isnan(la):
        la = 0
        ls = 0
        print("{:8} +- {:8} | {:4} | {:<5} |".format(
            "---", "---", "---", "---"), end = " ")
    else:
        #only contribute when we have a value
        lo=la/v
        los = np.append(los,lo)
        print("{:8.1f} +- {:8.1f} | {:.2f} | {:<5} |".format(
            la, ls, lo, sizes[nosync]), end=" ")

    try:
        print("{:.2f}".format(chronicler[experiment]), end = " | ")
    except KeyError:
        print(" ---", end=" | ")

    if isnan(fa):
        fa = 0
        fs = 0
        print("{:8} +- {:8} | {:4} | {:<5}".format("---", "---", "---", "---"))
    else:
        fo=fa/v
        fos = np.append(fos,fo)
        print("{:8.1f} +- {:8.1f} | {:.2f} | {:<5}".format(fa, fs, fo, sizes[sync]))

    #print("{:<10} | {:8.1f} +- {:8.1f} | {:8.1f} +- {:8.1f} | {:.2f} | {:<5} |".format(
    #    experiment,
    #    v, vs,
    #    la, ls, lo, sizes[nosync] 
    #    ), end= " ")

    #print("{:8.1f} +- {:8.1f} | {:.2f} | {:<5} ".format(fa, fs, fo, sizes[sync]))

    return (los,fos)

def printTxt():
    los=np.array([])
    fos=np.array([])
    print("{:<10} | {:20} | {:20} | {:4.3} | {:5} | {:4.3} | {:20} | {:4.3} | {:5} ".format("Bench", "Vanilla", "NoSync Record", "AVG", "Size", "ChroniclerJ", "Sync Record", "AVG", "Size"))
    (los,fos) = printLineTxt("avrora"  , "vanilla-rec-avrora.txt"  , "jmvx-rec-nosync-avrora.txt"  , "jmvx-rec-avrora.txt"  , los, fos)
    (los,fos) = printLineTxt("batik"   , "vanilla-rec-batik.txt"   , "jmvx-rec-nosync-batik.txt"   , "jmvx-rec-batik.txt"   , los, fos)
    (los,fos) = printLineTxt("fop"     , "vanilla-rec-fop.txt"     , "jmvx-rec-nosync-fop.txt"     , "jmvx-rec-fop.txt"     , los, fos)
    (los,fos) = printLineTxt("h2"      , "vanilla-rec-h2.txt"      , "jmvx-rec-nosync-h2.txt"      , "jmvx-rec-h2.txt"      , los, fos)
    (los,fos) = printLineTxt("h2 server"      , "vanilla-rec-h2cs.txt"      , "jmvx-rec-nosync-h2cs.txt"      , "jmvx-rec-h2cs.txt"      , los, fos)
    (los,fos) = printLineTxt("jme   ", "vanilla-rec-jme.txt"   , "jmvx-rec-nosync-jme.txt"   , "jmvx-rec-jme.txt"   , los, fos)
    (los,fos) = printLineTxt("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rec-nosync-jython.txt"  , "jmvx-rec-jython.txt"  , los, fos)
    (los,fos) = printLineTxt("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rec-nosync-luindex.txt" , "jmvx-rec-luindex.txt" , los, fos)
    (los,fos) = printLineTxt("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rec-nosync-lusearch.txt", "jmvx-rec-lusearch.txt", los, fos)
    (los,fos) = printLineTxt("pmd     ", "vanilla-rec-pmd.txt"     , "jmvx-rec-nosync-pmd.txt"     , "jmvx-rec-pmd.txt"     , los, fos)
    (los,fos) = printLineTxt("sunflow ", "vanilla-rec-sunflow.txt" , "jmvx-rec-nosync-sunflow.txt" , "jmvx-rec-sunflow.txt" , los, fos)
    (los,fos) = printLineTxt("xalan   ", "vanilla-rec-xalan.txt"   , "jmvx-rec-nosync-xalan.txt"   , "jmvx-rec-xalan.txt"   , los, fos)
    los = los[np.isfinite(los)]
    fos = fos[np.isfinite(fos)]
    print("{:<10} | {:20} | {:20} | {:4.2f} | {:5} | {:4.2f} | {:20} | {:4.2f} | {:5}".format("AVG", "", "", np.average(los), "", chronicler_a, "", np.average(fos), ""))

def printLineRecLaTeX(experiment, vanilla, nosync, sync,los,fos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    la = np.average(results[nosync])
    ls = np.std(results[nosync])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    lo = 0
    fo = 0
    if isnan(la):
        la = 0
        ls = 0
    elif(v != 0):
        lo=la/v
        los = np.append(los,lo)

    if isnan(fa):
        fa = 0
        fs = 0
    elif(v != 0):
        fo=fa/v
        fos = np.append(fos,fo)

    bench = experiment.strip() #key for mem
    if(bench == "h2 server"):
        bench = "h2cs"

    ss = ""
    try:
        ss = r"\textsuperscript{" + superscript[bench] + "}"
    except KeyError:
        pass

    print(r"{:<25}".format(experiment + ss) +
          " & $ {:.1f} \pm {:.1f} $ & $ {:.2f} \\times $ & $ {:.2f} \\times $ & {:<5} &".format(
        v, vs,
        lo, rec_nosync_over.get(bench, 0), sizes[nosync]
        ), end=" ")
    try:
        print("$ {:.2f} \\times $ &".format(chronicler[experiment]), end=" ")
    except KeyError:
        print("--- &", end=" ")

    print("$ {:.2f} \\times $ & $ {:.2f} \\times $ & {:<5} &".format(fo, rec_over.get(bench,0), sizes[sync]))

    return (los,fos)

def printLineRepLaTeX(experiment, vanilla, nosync, sync,os,nos):
    v  = np.average(results[vanilla])
    vs = np.std(results[vanilla])
    fa = np.average(results[sync])
    fs = np.std(results[sync])

    bench = experiment.strip() #key for mem
    if(bench == "h2 server"):
        bench = "h2cs"
    
    o = 0
    if isnan(fa):
        fa = 0
        fs = 0
    elif(v != 0):
        o=fa/v
        os = np.append(os,o)

    ns_str = ""
    if results[nosync] is not None and results[nosync].size > 0:
        na = np.average(results[nosync])
        ns = np.std(results[nosync])
        if v == 0 or na == 0 or isnan(na):
            na = 0
            ns = 0
            no = 0
            ns_str = "--- & --- "
            #this really should be filtered earlier. To do later
            del rep_nosync_over[bench]
        else:
            no=na/v
            nos = np.append(nos,no)
            ns_str = "$ {:.2f} \\times $ & $ {:.2f} \\times $ ".format(no,rep_nosync_over.get(bench, 0))
    else:
        na = 0
        ns = 0
        no = 0
        ns_str = "--- & --- "
        del rep_nosync_over[bench]

    print("{} & $ {:.2f} \\times $ & $ {:.2f} \\times $ \\\\".format(ns_str,o,rep_over.get(bench,0)))
    print(r"\hline")

    return (os,nos)

def printLaTeX():
    print(r"""
    \begin{tabular}{| l | c | c | c | c | c | c | c | c | c | c | c | c |}
        \hline
        \multirow{3}{*}{\textbf{Program}} & \multirow{2}{*}{\textbf{Vanilla}} & \multicolumn{7}{c|}{\textbf{Record}} & \multicolumn{4}{c|}{\textbf{Replay}} \\
        \cline{3-13}
        & & \multicolumn{3}{c|}{\textbf{No Sync}} & \multirow{2}{*}{\cite{chroniclerj}} & \multicolumn{3}{c|}{\textbf{Sync}} & \multicolumn{2}{c|}{\textbf{No Sync}} & \multicolumn{2}{c|}{\textbf{Sync}} \\
        \cline{2-5}
        \cline{7-13}
        & \emph{Time} & \emph{Perf} & \emph{Mem} & \emph{Size} & & \emph{Perf} & \emph{Mem} & \emph{Size} & \emph{Perf} & \emph{Mem} & \emph{Perf} & \emph{Mem} \\
        
        """)

    los=np.array([])
    fos=np.array([])

    los_rep=np.array([])
    fos_rep=np.array([])

    print(r"\hline")
    (los,fos)         = printLineRecLaTeX("avrora"    , "vanilla-rec-avrora.txt"  , "jmvx-rec-nosync-avrora.txt"  , "jmvx-rec-avrora.txt"  , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("avrora"    , "vanilla-rec-avrora.txt"  , "jmvx-rep-nosync-avrora.txt"  , "jmvx-rep-avrora.txt"  , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("batik"     , "vanilla-rec-batik.txt"   , "jmvx-rec-nosync-batik.txt"   , "jmvx-rec-batik.txt"   , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("batik"     , "vanilla-rec-batik.txt"   , "jmvx-rep-nosync-batik.txt"   , "jmvx-rep-batik.txt"   , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("fop"       , "vanilla-rec-fop.txt"     , "jmvx-rec-nosync-fop.txt"     , "jmvx-rec-fop.txt"     , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("fop"       , "vanilla-rec-fop.txt"     , "jmvx-rep-nosync-fop.txt"     , "jmvx-rep-fop.txt"     , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("h2"        , "vanilla-rec-h2.txt"      , "jmvx-rec-nosync-h2.txt"      , "jmvx-rec-h2.txt"      , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("h2"        , "vanilla-rec-h2.txt"      , "jmvx-rep-nosync-h2.txt"      , "jmvx-rep-h2.txt"      , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("h2 server" , "vanilla-rec-h2cs.txt"    , "jmvx-rec-nosync-h2cs.txt"    , "jmvx-rec-h2cs.txt"    , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("h2 server" , "vanilla-rec-h2cs.txt"    , "jmvx-rep-nosync-h2cs.txt"    , "jmvx-rep-h2cs.txt"    , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("jme"       , "vanilla-rec-jme.txt"   , "jmvx-rec-nosync-jme.txt"   , "jmvx-rec-jme.txt"   , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("jme"       , "vanilla-rec-jme.txt"   , "jmvx-rep-nosync-jme.txt"   , "jmvx-rep-jme.txt"   , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("jython"    , "vanilla-rec-jython.txt"  , "jmvx-rec-nosync-jython.txt"  , "jmvx-rec-jython.txt"  , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("jython"    , "vanilla-rec-jython.txt"  , "jmvx-rep-nosync-jython.txt"  , "jmvx-rep-jython.txt"  , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("luindex"   , "vanilla-rec-luindex.txt" , "jmvx-rec-nosync-luindex.txt" , "jmvx-rec-luindex.txt" , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("luindex"   , "vanilla-rec-luindex.txt" , "jmvx-rep-nosync-luindex.txt" , "jmvx-rep-luindex.txt" , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("lusearch"  , "vanilla-rec-lusearch.txt", "jmvx-rec-nosync-lusearch.txt", "jmvx-rec-lusearch.txt", los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("lusearch"  , "vanilla-rec-lusearch.txt", "jmvx-rep-nosync-lusearch.txt", "jmvx-rep-lusearch.txt", los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("pmd"       , "vanilla-rec-pmd.txt"     , "jmvx-rec-nosync-pmd.txt"     , "jmvx-rec-pmd.txt"     , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("pmd"       , "vanilla-rec-pmd.txt"     , "jmvx-rep-nosync-pmd.txt"     , "jmvx-rep-pmd.txt"     , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("sunflow"   , "vanilla-rec-sunflow.txt" , "jmvx-rec-nosync-sunflow.txt" , "jmvx-rec-sunflow.txt" , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("sunflow"   , "vanilla-rec-sunflow.txt" , "jmvx-rep-nosync-sunflow.txt" , "jmvx-rep-sunflow.txt" , los_rep, fos_rep)
    (los,fos)         = printLineRecLaTeX("xalan"     , "vanilla-rec-xalan.txt"   , "jmvx-rec-nosync-xalan.txt"   , "jmvx-rec-xalan.txt"   , los, fos)
    (los_rep,fos_rep) = printLineRepLaTeX("xalan"     , "vanilla-rec-xalan.txt"   , "jmvx-rep-nosync-xalan.txt"   , "jmvx-rep-xalan.txt"   , los_rep, fos_rep)


    los = los[np.isfinite(los)]
    fos = fos[np.isfinite(fos)]
    los_rep = los_rep[np.isfinite(los_rep)]
    fos_rep = los[np.isfinite(los)]
    print(r"\hline")
    print(r"\textbf{AVG}" + r" & --- & ${:.2f} \times$ & ${:.2f} \times$ & --- & ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ & --- & ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ & ${:.2f} \times$ \\".format(
        np.average(los), mem.avg_maxes(rec_nosync_over), #rec nosync
        chronicler_a, #chronicler
        np.average(fos), mem.avg_maxes(rec_over), #rec sync
        np.average(fos_rep), mem.avg_maxes(rep_nosync_over), #rep nosync
        np.average(los_rep), mem.avg_maxes(rep_over))) #rep
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
    los = np.append(los,lo)
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
    (los,fos) = printLineCsv("jme", "vanilla-rec-jme.txt"   , "jmvx-rec-nosync-jme.txt"   , "jmvx-rec-jme.txt"   , los, fos)
    (los,fos) = printLineCsv("jython"  , "vanilla-rec-jython.txt"  , "jmvx-rec-nosync-jython.txt"  , "jmvx-rec-jython.txt"  , los, fos)
    (los,fos) = printLineCsv("luindex" , "vanilla-rec-luindex.txt" , "jmvx-rec-nosync-luindex.txt" , "jmvx-rec-luindex.txt" , los, fos)
    (los,fos) = printLineCsv("lusearch", "vanilla-rec-lusearch.txt", "jmvx-rec-nosync-lusearch.txt", "jmvx-rec-lusearch.txt", los, fos)
    (los,fos) = printLineCsv("pmd", "vanilla-rec-pmd.txt"     , "jmvx-rec-nosync-pmd.txt"     , "jmvx-rec-pmd.txt"     , los, fos)
    (los,fos) = printLineCsv("sunflow", "vanilla-rec-sunflow.txt" , "jmvx-rec-nosync-sunflow.txt" , "jmvx-rec-sunflow.txt" , los, fos)
    (los,fos) = printLineCsv("xalan", "vanilla-rec-xalan.txt"   , "jmvx-rec-nosync-xalan.txt"   , "jmvx-rec-xalan.txt"   , los, fos)
    los = los[np.isfinite(los)]
    fos = fos[np.isfinite(fos)]
    print("{},,,,,{:.2f},,{:.2f},,,{:.2f},".format("AVG", np.average(los), chronicler_a, np.average(fos)))

if output == 'txt':
    printTxt()
if output == 'latex':
    printLaTeX()
if output == 'csv':
    printCsv()
