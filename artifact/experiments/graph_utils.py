#!/usr/bin/python3
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
from matplotlib import font_manager
from os import path
import csv
import sys

# ACM camera ready cannot use type 3 font,
# which, by default, is how matplotlib embeds the font
# this changes matplotlib's config to export as a truetype instead
plt.rcParams.update({'pdf.fonttype': 42})
FIGSIZE = (14,8)
plt.rc("legend", fontsize=12)
plt.rc("axes", titlesize=18, labelsize=18)
plt.rc("xtick", labelsize=12)
plt.rc("ytick", labelsize=12)
#plt.rcParams.update({'font.size': 18})
fmt="png"
infolder = '.'
outfolder = '.'
try:
	infolder = sys.argv[1]
	outfolder = sys.argv[2]
	fmt = sys.argv[3]
except IndexError:
	pass

def read_csv(fname, import_func=lambda x: x):
	"""Creates a list of dicts with column,value pairs from a csv file
	This is not memory efficient nor designed to deal with large amounts of data"""
	rows = []
	with open(fname, "r") as f:
		reader = csv.DictReader(f)
		for row in reader:
			rows.append(row)
	return rows

def size_to_num(size):
	if(len(size) == 0):
		return 0
	unit = size[-1]
	num = float(size[:-1])
	if(unit == "G"):
		num *= 1024
	return num

def convert_sizes(data):
	return {k: [size_to_num(v) for v in vs] for k,vs in data.items()}

def convert_numbers(data, dtype=float):
	"""Attempts to convert all numbers in a loaded csv to dtype
	By default, dtype is float"""
	for row in data:
		for k,v in row.items():
			#fill in missing value
			try:
				if("size" in k):
					row[k] = size_to_num(v)
				else:
					row[k] = dtype(v)
			except (ValueError, TypeError):
				#fix missing value
				if(v == ""):
					row[k] = 0.0
	return data

def normalize(data):
	for row in data:
		vanilla = row["vanilla"]
		for k,v in row.items():
			if("experiment" == k or "size" in k or "over" in k):
				#headers we don't want to modify
				continue
			else:
				try:
					row[k] = v/vanilla
				except TypeError:
					pass
	return data

def bar_from_table(ax, data, role_order, colors, errs=None, xtick_offset=0, legend_subst=None):
	"""Make a grouped bar chart, data comes from a table (list of dicts of col, value pairs)
	ax - figure axes, from pyplot.figure or pyplot.subplots
	data - data from a csv, list of dicts
	role_order - list of column names, orders the bars
	colors - list of colors to use per bar (len == len role_order)
	errs - optional, keys to get error from, can be None
	xtick_offset - optional, amount to shift xtick, use to center them"""
	#Note, if y axis disappers, it may be due to trying to plot a string value (e.g., "")
	#That breaks the calc for the y axis, and plt just drops it
	bar_width = 1/(1+len(role_order)) 
	x = 0
	if(errs == None):
		errs = [None]*len(role_order)
	for row in data:
		for r,c,e in zip(role_order,colors,errs):
			if(e):
				ax.bar(x, row[r], bar_width, color=c, yerr=row[e], capsize=2.5)
			else:
				ax.bar(x, row[r], bar_width, color=c)
			x = x + bar_width
		x = x + bar_width #(blank space)

	xticks = [i + xtick_offset for i in range(len(data))]
	bms = [row["experiment"] for row in data]
	for i in range(len(bms)):
		if(i % 2):
			bms[i] = f"\n{bms[i]}"
	ax.set_xticks(xticks, bms)
	legend_contents = legend_subst if legend_subst else role_order
	ax.legend(legend_contents)

def gather_data(mode, column, settings):
	#grab all data
	data = [convert_numbers(read_csv(path.join(infolder, mode + str(i) + ".csv"))) for i in settings]
	#reformat
	perf = {}

	for table in data:
		for row in table:
			bench = row["experiment"]
			if(bench in perf):
				perf[bench].append(row[column])
			else:
				perf[bench] = [row[column]]
	return perf

def bar_from_dict(ax, perf, settings):
	"""Data has been gathered into dict of:
	benchmark => measurements"""
	bar_width = 0.25#1/(1+len(role_order)) 
	x = 0
	#if(errs == None):
	#	errs = [None]*len(role_order)
	i = 0
	order = [None]*len(perf) #ensures we get same order for x axis
	xticks = [0]*len(perf)
	colors = list(mcolors.TABLEAU_COLORS)[:9]
	for bench,overheads in perf.items():
		j = 0
		for o in overheads:
			ax.bar(x, o, bar_width, color=colors[j])
			#apparently I'm not smart enough to solve a simple equation for this
			#so I'm just going to make the computer store the value for the x tick
			#as it places bars
			if(j == len(overheads) // 2):
				xticks[i] = x + bar_width/2
			x = x + bar_width
			j = j + 1
		x = x + 2*bar_width #blank space
		order[i] = bench
		i = i + 1

	for i in range(len(order)):
		if(i % 2):
			order[i] = f"\n{order[i]}"

	ax.set_xticks(xticks, order)

