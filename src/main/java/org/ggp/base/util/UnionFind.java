package org.ggp.base.util;

/**
 * UnionFind implementation attributed to
 * http://www.cs.waikato.ac.nz/~bernhard/317/source/graph/UnionFind.java
 * */

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UnionFind {

  private int[] _parent;
  private int[] _rank;


  public int find(int i) {

    int p = _parent[i];
    if (i == p) {
      return i;
    }
    return _parent[i] = find(p);

  }


  public void union(int i, int j) {

    int root1 = find(i);
    int root2 = find(j);

    if (root2 == root1) return;

    if (_rank[root1] > _rank[root2]) {
      _parent[root2] = root1;
    } else if (_rank[root2] > _rank[root1]) {
      _parent[root1] = root2;
    } else {
      _parent[root2] = root1;
      _rank[root1]++;
    }
  }

  public void print_num_groups() {
	  Set<Integer> group_nums = new HashSet<Integer>();

	  for(int i : _parent) {
		  group_nums.add(i);
	  }

	  System.out.println("Number of groups: " + group_nums.size());
  }


  public UnionFind(int max) {

    _parent = new int[max];
    _rank = new int[max];

    for (int i = 0; i < max; i++) {
      _parent[i] = i;
    }
  }


  @Override
  public String toString() {
    return "<UnionFind\np " + Arrays.toString(_parent) + "\nr " + Arrays.toString(_rank) + "\n>";
  }
}