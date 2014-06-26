package org.ethereum.trie;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.Value;
import org.iq80.leveldb.DB;

/*
 * www.ethereumJ.com
 * @author: Nick Savers
 * Created on: 20/05/2014 10:44
 */
public class Cache {
	
	private Map<ByteArrayWrapper, Node> nodes;
	private DB db;
	private boolean isDirty;

	public Cache(DB db) {
		this.db = db;
		nodes = new HashMap<ByteArrayWrapper, Node>();
	}

	/**
	 * Put the node in the cache if RLP encoded value is longer than 32 bytes
	 * 
	 * @param o the Node which could be a pair-, multi-item Node or single Value  
	 * @return sha3 hash of RLP encoded node if length > 32 otherwise return node itself
	 */
	public Object put(Object o) {
		Value value = new Value(o);
		byte[] enc = value.encode();
		if (enc.length >= 32) {
			byte[] sha = HashUtil.sha3(enc);
			this.nodes.put(new ByteArrayWrapper(sha), new Node(value, true));
			this.isDirty = true;
			return sha;
		}
		return value;
	}

	public Value get(byte[] key) {
		ByteArrayWrapper keyObj = new ByteArrayWrapper(key);
		// First check if the key is the cache
		if (this.nodes.get(keyObj) != null) {
			return this.nodes.get(keyObj).getValue();
		}

        if (db != null) return new Value(null);

		// Get the key of the database instead and cache it
		byte[] data = this.db.get(key);
		Value value = new Value(data);
		// Create caching node
		this.nodes.put(keyObj, new Node(value, false));

		return value;
	}

	public void delete(byte[] key) {
		ByteArrayWrapper keyObj = new ByteArrayWrapper(key);
		this.nodes.remove(keyObj);

        if (db == null) return;
		this.db.delete(key);
	}

	public void commit() {

        if (db == null) return;

		// Don't try to commit if it isn't dirty
		if (!this.isDirty) {
			return;
		}

		for (ByteArrayWrapper key : this.nodes.keySet()) {
			Node node = this.nodes.get(key);
			if (node.isDirty()) {
				this.db.put(key.getData(), node.getValue().encode());
				node.setDirty(false);
			}
		}
		this.isDirty = false;

		// If the nodes grows beyond the 200 entries we simple empty it
		// FIXME come up with something better
		if (this.nodes.size() > 200) {
			this.nodes = new HashMap<ByteArrayWrapper, Node>();
		}
	}

	public void undo() {
		Iterator<Map.Entry<ByteArrayWrapper, Node>> iter = this.nodes.entrySet().iterator();
		while (iter.hasNext()) {
		    if(iter.next().getValue().isDirty()){
		        iter.remove();
		    }
		}
		this.isDirty = false;
	}

	public boolean isDirty() {
		return isDirty;
	}

	public void setDirty(boolean isDirty) {
		this.isDirty = isDirty;
	}

	public Map<ByteArrayWrapper, Node> getNodes() {
		return nodes;
	}

	public DB getDb() {
		return db;
	}
}
