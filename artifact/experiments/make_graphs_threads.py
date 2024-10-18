#!/usr/bin/python3
from graph_utils import *
from os import path
import sys

#make graphs of the data
#usage python3 make_graphs [infolder] [outfolder]
#if no directories are supplied, '.' is assumped
#this script looks in <infolder> for csv versions of the tables:
#	instr.csv, mvx.csv, rec.csv, rep.csv
#and makes svg graphs of them in <outfolder>
#If a file doesn't exist, it is skipped.
#You'll need to use the table- scripts first

def make_graph(out, mode, column, title, ymax=-1, legend_cols=1, fontsize=None):
	settings = [i+1 for i in range(9)]
	perf = gather_data(mode, column, settings)
	fig, ax = plt.subplots()#figsize=FIGSIZE)
	ax.set_title(title)
	ax.set_ylabel("Normalized Execution Time")
	ax.set_xlabel("Benchmark")
	bar_from_dict(ax, perf, settings)
	if(legend_cols > 0):
		ax.legend([i + 1 for i in range(9)], ncols=legend_cols, loc="upper right", fontsize=fontsize)
	ax.axhline(y=1, linestyle="--", color="black")
	if(ymax > 0):
		ax.set_ylim(top=ymax)
	#plt.show()
	plt.tight_layout()
	outfile = path.join(outfolder, out)
	print("Saved graph to", outfile)
	plt.savefig(outfile, format=out[out.rindex('.')+1:])


def make_rec_size_graph(out, legend_cols=0):
	settings = [i+1 for i in range(9)]
	sync = gather_data("rr", "sync_size", settings)
	del sync["AVG"]
	#nosync = gather_data("rec", "nosync_size", settings)
	#del nosync["AVG"]
	fig, ax = plt.subplots()#figsize=FIGSIZE)
	ax.set_title("Recording Size Over Threads")
	ax.set_ylabel("Size (MB), log scale")
	ax.set_xlabel("Benchmark")
	bar_from_dict(ax, sync, settings)
	ax.set_yscale('log')
	if(legend_cols > 0):
		ax.legend([i + 1 for i in range(9)], ncols=legend_cols, loc="upper right")
	#ax.set_ylim(top=1900)
	#plt.show()
	plt.tight_layout()
	outfile = path.join(outfolder, out)
	print("Saved graph to", outfile)
	plt.savefig(outfile, format=out[out.rindex('.')+1:])

make_graph(f"leader_threads.{fmt}", "mvx", "leader_over", "Leader Performance Over Threads", ymax=3, legend_cols=2, fontsize="small")
make_graph(f"rec_threads.{fmt}", "rr", "sync_over", "Recorder Performance Over Threads", ymax=3, legend_cols=2)
make_graph(f"follower_threads.{fmt}", "mvx", "follower_over", "Follower Performance Over Threads", ymax=3, legend_cols=-1)
make_graph(f"rep_threads.{fmt}", "rep", "sync_over", "Replayer Performance Over Threads", ymax=3, legend_cols = -1)
make_rec_size_graph(f"rec_sizes.{fmt}", legend_cols=2)
