/* 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.search.backend.impl.lucene;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.hibernate.search.exception.ErrorHandler;
import org.hibernate.search.indexes.impl.DirectoryBasedIndexManager;
import org.hibernate.search.indexes.spi.ReaderProvider;
import org.hibernate.search.util.logging.impl.Log;
import org.hibernate.search.util.logging.impl.LoggerFactory;

/**
 * The Workspace implementation to be used to take advantage of NRT Lucene features.
 * IndexReader instances are obtained directly from the IndexWriter, which is not forced
 * to flush all pending changes to the Directory structure.
 * 
 * We keep a reference Reader, obtained from the IndexWriter each time a transactional queue
 * is applied, so that the IndexReader instance "sees" only fully committed transactions;
 * the reference is never returned to clients, but each time a client needs an IndexReader
 * a clone is created from the last refreshed IndexReader.
 * 
 * Since the backend is forced to create a reference IndexReader after each (skipped) commit,
 * some IndexReaders might be opened without being ever used.
 * 
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2011 Red Hat Inc.
 */
public class NRTWorkspaceImpl extends AbstractWorkspaceImpl implements ReaderProvider {

	private static final Log log = LoggerFactory.make();

	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

	private final ReadLock readLock = readWriteLock.readLock();
	private final WriteLock writeLock = readWriteLock.writeLock();
	private final String indexName;

	//guardedBy readLock/writeLok
	private IndexReader currentReferenceReader = null;

	public NRTWorkspaceImpl(DirectoryBasedIndexManager indexManager, ErrorHandler errorHandler, Properties cfg) {
		super( indexManager, errorHandler );
		indexName = indexManager.getIndexName();
	}

	@Override
	public void afterTransactionApplied() {
		IndexReader newIndexReader = writerHolder.openNRTIndexReader( true );
		writeLock.lock();
		IndexReader oldReader = currentReferenceReader;
		currentReferenceReader = newIndexReader;
		writeLock.unlock();
		try {
			oldReader.close();
		}
		catch ( IOException e ) {
			log.unableToCLoseLuceneIndexReader( e );
		}
	}

	@Override
	public IndexReader openIndexReader() {
		readLock.lock();
		try {
			if ( currentReferenceReader == null ) {
				readLock.unlock();
				writeLock.lock();
				try {
					if ( currentReferenceReader == null) {
						currentReferenceReader = writerHolder.openDirectoryIndexReader();
					}
				}
				finally {
					writeLock.unlock();
				}
				readLock.lock();
			}
			return cloneReader( currentReferenceReader );
		}
		finally {
			readLock.unlock();
		}
	}

	/**
	 * We need to return clones so that each reader can be closed independently;
	 * clones should share most heavy-weight buffers anyway.
	 */
	private IndexReader cloneReader(IndexReader indexReader) {
		try {
			return indexReader.clone( true );
		}
		catch ( CorruptIndexException cie ) {
			throw log.cantOpenCorruptedIndex( cie, indexName );
		}
		catch ( IOException ioe ) {
			throw log.ioExceptionOnIndex( ioe, indexName );
		}
	}

	@Override
	public void closeIndexReader(IndexReader reader) {
		try {
			reader.close();
		}
		catch ( IOException e ) {
			log.unableToCLoseLuceneIndexReader( e );
		}
	}

}