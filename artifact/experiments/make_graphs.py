#!/usr/bin/python3
from graph_utils import *
from os import path
import sys

#args: [infolder] [outfolder] [format]
#these args are processed by graph_utils when it is imported

def make_perf_graph(fname, out, title, role_order, colors, xtick_offset, legend_subst=None):
	file = path.join(infolder, fname)
	if(not path.exists(file)):
		print(file, "does not exist, skipping")
		return
	data = read_csv(file)
	#data = data[:-1] #drop last row
	convert_numbers(data)
	#normalize(data)
	#role_order=("nosync", "sync")
	#figsize is a const in graph_utils
	fig, ax = plt.subplots()#figsize=FIGSIZE)
	ax.set_title(title)
	ax.set_ylabel("Normalized Execution Time")
	ax.set_xlabel("Benchmark")
	bar_from_table(ax, data, role_order, colors,  xtick_offset=xtick_offset, legend_subst=legend_subst)
	ax.axhline(y=1, linestyle="--", color="black")
	plt.tight_layout()
	#plt.show()
	outfile = path.join(outfolder, out)
	print("Saved graph to", outfile)
	plt.savefig(outfile, format=out[out.rindex('.')+1:])

def make_merged_perf_graph(fname, out, title, role_order, colors, xtick_offset):
	d1 = read_csv(path.join(infolder, fname[0]))
	d2 = read_csv(path.join(infolder, fname[1]))
	merged = [{"experiment": a["experiment"], "vanilla": a["vanilla"], role_order[0]: a["sync_over"], role_order[1]: b["sync_over"]} for a,b in zip(d1, d2)]
	data = merged#merged[:-1] #drop last row
	convert_numbers(data)
	#normalize(data)
	#role_order=("nosync", "sync")
	#figsize is a const in graph_utils
	fig, ax = plt.subplots()#figsize=FIGSIZE)
	ax.set_title(title)
	ax.set_ylabel("Normalized Execution Time")
	ax.set_xlabel("Benchmark")
	bar_from_table(ax, data, role_order, colors,  xtick_offset=xtick_offset)
	ax.axhline(y=1, linestyle="--", color="black")
	plt.tight_layout()
	#plt.show()
	outfile = path.join(outfolder, out)
	print("Saved graph to", outfile)
	plt.savefig(outfile, format=out[out.rindex('.')+1:])
    


#make_perf_graph("instr.csv", f"instr.{fmt}", "Instrumentation Overhead", ["nosync", "sync"], ("tab:orange","tab:blue"), xtick_offset=0.15)
make_perf_graph("mvx.csv", f"mvx.{fmt}", "MVX Overhead", ["leader_over", "follower_over"], ("tab:orange","tab:blue"), xtick_offset=0.15, legend_subst=["Leader", "Follower"])
#make_perf_graph("rec.csv", f"rec.{fmt}", "Record Overhead", ["nosync", "sync"], ("tab:orange","tab:blue"), xtick_offset=0.15)
#make_perf_graph("rep.csv", f"rep.{fmt}", "Replay Overhead", ["nosync", "sync"], ("tab:orange","tab:blue"), xtick_offset=0.15)
make_merged_perf_graph(("rr.csv", "rep.csv"), f"rr.{fmt}", "Recorder/Replayer Overhead", ["Recorder", "Replayer"], ("tab:orange", "tab:blue"), xtick_offset=0.15)
