#!/usr/bin/python3
from graph_utils import *
from os import path

#make graphs of the data
#usage python3 make_graphs [infolder] [outfolder]
#if no directories are supplied, '.' is assumped
#this script looks in <infolder> for csv versions of the tables:
#	instr.csv, mvx.csv, rec.csv, rep.csv
#and makes svg graphs of them in <outfolder>
#If a file doesn't exist, it is skipped.
#You'll need to use the table- scripts first

def make_graph(out, mode, column, title, legend_cols = 1, ymax=0):
	settings = [1, 10, 100, 250, 500]
	perf = gather_data(mode, column, settings)
	fig, ax = plt.subplots()#figsize=FIGSIZE)
	ax.set_title(title)
	ax.set_ylabel("Normalized Execution Time")
	ax.set_xlabel("Benchmark")
	bar_from_dict(ax, perf, settings)
	if(legend_cols > 0):
		ax.legend([f"{s} MB" for s in settings], loc="upper left", ncols=legend_cols)
	if(ymax > 0):
		ax.set_ylim(top=ymax)
	#ax.annotate(perf["lusearch"][1])
	ax.axhline(y=1, linestyle="--", color="black")
	plt.tight_layout()
	#plt.show()
	outfile = path.join(outfolder, out)
	print("Saved graph to", outfile)
	plt.savefig(outfile, format=out[out.rindex('.')+1:])

make_graph(f"leader_buf.{fmt}", "mvx", "leader_over", "Leader Performance Over Circular Buffer", legend_cols=1, ymax=3)
make_graph(f"follower_buf.{fmt}", "mvx", "follower_over", "Follower Performance Over Circular Buffer", legend_cols=1, ymax=6)
