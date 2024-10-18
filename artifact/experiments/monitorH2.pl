#!/usr/bin/perl
use strict;
use warnings;
use Time::HiRes qw( sleep );

#watch active connections in here
my %conn = ();
#number of programs we've killed
my $killed = 0;

#state for iteration
my $pid = -1;
my $listen = 0;
my $active = 0;

sub add_conn{
	if($pid > 0 and $listen){
		$conn{$pid} = $active;
	}
	#reset for new process
	$listen = 0;
	$active = 0;
}

#someone still is listening on the port
while(%conn > 0 or $pid == -1){
	#empty hash, useful in case the program crashes
	%conn = (); 
	$pid = 0;
	#use lsof to find all processes involved in a connection with port 9092 (h2)
	#and are listening to that port
	for(split /\n/, `lsof -i :9092 -P -n -F T`){
		if(/p(\d+)/){
			add_conn;
			$pid = $1;
		}elsif(/ST=(\w+)/){
			$listen++ if $1 eq "LISTEN";
			$active++ if $1 ne "CLOSED";
		}
	}

	#end of iter may have an active connection
	add_conn;

	next if %conn == 0;
	while(my($id, $count) = each %conn){
		if($count == 1){
			#arg list contains extra signals to send if we care to
			#don't need this for jmvx,
			#b/c jmvx sends quit to itself while handling INT
			#but vanilla does not, so this is needed
			for(@ARGV){
				kill $_, $id;
			}
			kill "USR2", $id;
			kill "TERM", $id;
			$killed++;
			delete $conn{$id};
			print "$id stopped\n";
		}
	}

	sleep 0.25; #250 ms needs time high res for this!
}
