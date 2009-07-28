// -*- mode:Java; tab-width:8; c-basic-offset:2; indent-tabs-mode:t -*- 
package org.apache.hadoop.fs.ceph;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Set;
import java.util.EnumSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.CreateFlag;

/**
 * <p>
 * A {@link FileSystem} backed by <a href="http://ceph.sourceforge.net">Ceph.</a>.
 * This will not start a Ceph instance; one must already be running.
 * </p>
  */
public class CephFileSystem extends FileSystem {

  private static final long DEFAULT_BLOCK_SIZE = 8 * 1024 * 1024;
  
  static {
    System.load("/usr/local/lib/libhadoopcephfs.so");
  }
  
  private URI uri;

  private FileSystem localFs;
  
  private Path root;

  private Path parent;
  
  //private Path workingDir = new Path("/user", System.getProperty("user.name"));
  
  
  private native boolean ceph_initializeClient();
  private native boolean ceph_copyFromLocalFile(String localPath, String cephPath);
  private native boolean ceph_copyToLocalFile(String cephPath, String localPath);
  private native String  ceph_getcwd();
  private native boolean ceph_setcwd(String path);
  private native boolean ceph_rmdir(String path);
  private native boolean ceph_mkdir(String path);
  private native boolean ceph_unlink(String path);
  private native boolean ceph_rename(String old_path, String new_path);
  private native boolean ceph_exists(String path);
  private native long    ceph_getblocksize(String path);
  private native long    ceph_getfilesize(String path);
  private native boolean ceph_isdirectory(String path);
  private native boolean ceph_isfile(String path);
  private native String[] ceph_getdir(String path);
  private native int ceph_mkdirs(String path, int mode);
  private native int ceph_open_for_append(String path);
  private native int ceph_open_for_read(String path);
  private native int ceph_open_for_overwrite(String path, int mode);
  private native boolean ceph_kill_client();

  public CephFileSystem() {
    System.out.println("CephFileSystem:enter");
    root = new Path("/");
    parent = new Path("..");
    System.out.println("CephFileSystem:exit");
  }

  /*
    public S3FileSystem(FileSystemStore store) {
    this.store = store;
    } */

  public URI getUri() {
    System.out.println("getUri:enter");
    System.out.println("getUri:exit");
    return uri;
  }

  @Override
    public void initialize(URI uri, Configuration conf) throws IOException {
    System.out.println("initialize:enter");
    //store.initialize(uri, conf);
    setConf(conf);
    this.uri = URI.create(uri.getScheme() + "://" + uri.getAuthority());    

    // TODO: local filesystem? we really need to figure out this conf thingy
    this.localFs = get(URI.create("file:///"), conf);

    //  Initializes the client
    System.out.println("Calling ceph_initializeClient");
    if (!ceph_initializeClient()) {
      throw new IOException("Ceph initialization failed!");
    }
    System.out.println("Initialized client. Setting cwd to /");
    ceph_setcwd("/");
    // DEBUG
    // attempt to do three exists operations on root
    System.out.println("DEBUG: attempting isdir() on root (/)");
    System.out.println(ceph_isdirectory("/"));
    System.out.println("DEBUG: attempting exists() on root (/)");
    System.out.println(ceph_exists("/"));
    System.out.println("initialize:exit");
  }

  @Override
    public void close() throws IOException {
    System.out.println("close:enter");
    System.out.println("Pretending to shut down client. Not really doing anything.");
    System.out.println("close:exit");
  }

  public FSDataOutputStream append (Path file, int bufferSize,
				    Progressable progress) throws IOException {
    System.out.println("append:enter");
    Path abs_path = makeAbsolute(file);
    int fd = ceph_open_for_append(abs_path.toString());
    if( fd < 0 ) { //error in open
      throw new IOException("append: Open for append failed on path \"" +
			    abs_path.toString() + "\"");
    }
    CephOutputStream cephOStream = new CephOutputStream(getConf(), fd);
    System.out.println("append:exit");
    return new FSDataOutputStream(cephOStream);
  }

  public String getName() {
    System.out.println("getName:enter");
    System.out.println("getName:exit");
    return getUri().toString();
  }

  public Path getWorkingDirectory() {
    System.out.println("getWorkingDirectory:enter");
    System.out.println("getWorkingDirectory:exit");
    return makeAbsolute(new Path(ceph_getcwd()));
  }

  @Override
    public void setWorkingDirectory(Path dir) {
    System.out.println("setWorkingDirecty:enter");
    Path abs_path = makeAbsolute(dir);

    // error conditions if path's not a directory
    boolean isDir = false;
    boolean path_exists = false;
    try {
      isDir = isDirectory(abs_path);
      path_exists = exists(abs_path);
    }

    catch (IOException e) {
      System.out.println("Warning: isDirectory threw an exception");
    }

    if (!isDir) {
      if (path_exists)
	System.out.println("Warning: SetWorkingDirectory(" + dir.toString() + 
			   "): path is not a directory");
      else
	System.out.println("Warning: SetWorkingDirectory(" + dir.toString() + 
			   "): path does not exist");
    }
    else {
      ceph_setcwd(dir.toString());
    }
    //System.out.println("DEBUG: Attempting to change cwd to " + dir.toString() +
    //		 "changes cwd to" + getWorkingDirectory().toString());
    System.out.println("setWorkingDirectory:exit");
  }

  // Makes a Path absolute. In a cheap, dirty hack, we're
  // also going to strip off any "ceph://null" prefix we see. 
  private Path makeAbsolute(Path path) {
    System.out.println("makeAbsolute:enter");
    // first, check for the prefix
    if (path.toString().startsWith("ceph://null")) {
	  
      Path stripped_path = new Path(path.toString().substring("ceph://null".length()));
      //System.out.println("Stripping path \"" + path.toString() + "\" to \""
      //		     + stripped_path.toString() + "\"");
      return stripped_path;
    }


    if (path.isAbsolute()) {
      return path;
    }
    Path wd = getWorkingDirectory();
    //System.out.println("Working directory is " + wd.toString());
    if (wd.toString().equals(""))
      return new Path(root, path);
    else
      return new Path(wd, path);

    System.out.println("makeAbsolute:exit");
  }

  private String[] getEmptyStringArray(int size) {
    return new String[size];
  }

  @Override
    public boolean exists(Path path) throws IOException {
    boolean result;
    Path abs_path = makeAbsolute(path);
    if (abs_path.toString().equals("/"))
      {
	//System.out.println("Bug workaround! returning true for exists(/)");
	result = true;
      }
    else 
      {
	//System.out.println("Calling ceph_exists from Java on path " + abs_path.toString() + ":");
	result =  ceph_exists(abs_path.toString());
	//System.out.println("Returned from ceph_exists to Java");
      }
    // System.out.println("exists \"" + path.toString() + "\"? Absolute path is \"" +
    //		abs_path.toString() + "\", result = " + result);

    return result;
  }


  /* Creates the directory and all nonexistent parents.   */
  public boolean mkdirs(Path path, FsPermission perms) throws IOException {
    Path abs_path = makeAbsolute(path);
    int result = ceph_mkdirs(abs_path.toString(), (int)perms.toShort());
    /*System.out.println("mkdirs: attempted to make directory "
      + abs_path.toString() +  ": result is " + result); */
    if (result != 0)
      return true;
    else return false;
  }



  //   @Override

  public boolean __isDirectory(Path path) throws IOException {
    Path abs_path = makeAbsolute(path);
    boolean result;

    if (abs_path.toString().equals("/"))
      {
	//System.out.println("Bug workaround! returning true for isDirectory(/)");
	result = true;
      }
    else
      result = ceph_isdirectory(abs_path.toString());
    //System.out.println("isDirectory \"" + path.toString() + "\"? Absolute path is \"" +
    //		abs_path.toString() + "\", result = " + result);
    return result;
  }

  @Override
    public boolean isFile(Path path) throws IOException {
    Path abs_path = makeAbsolute(path);
    boolean result;
    if (abs_path.toString().equals("/"))
      {
	//System.out.println("Bug workaround! returning false for isFile(/)");
	result =  false;
      }
    else
      {
	result = ceph_isfile(abs_path.toString());
      }
    //System.out.println("isFile \"" + path.toString() + "\"? Absolute path is \"" +
    //	abs_path.toString() + "\", result = " + result);

    return result;
  }

  public FileStatus getFileStatus(Path p) throws IOException {
    // For the moment, hardwired replication and modification time
    Path abs_p = makeAbsolute(p);
    return new FileStatus(__getLength(abs_p), __isDirectory(abs_p), 2,
			  getBlockSize(p), 0, abs_p);
  }

  // array of statuses for the directory's contents
  // steal or factor out iteration code from delete()
    public FileStatus[] listStatus(Path p) throws IOException {
    Path abs_p = makeAbsolute(p);
    Path[] paths = listPaths(abs_p);
    FileStatus[] statuses = new FileStatus[paths.length];
    for (int i = 0; i < paths.length; ++i) {
      statuses[i] = getFileStatus(paths[i]);
    }
    return statuses;
  }


  public Path[] listPaths(Path path) throws IOException {

    String dirlist[];

    Path abs_path = makeAbsolute(path);

    //System.out.println("listPaths on path \"" + path.toString() + "\", absolute path \""
    //		 + abs_path.toString() + "\"");

    // If it's a directory, get the listing. Otherwise, complain and give up.
    if (isDirectory(abs_path))
      dirlist = ceph_getdir(abs_path.toString());
    else
      {
	if (exists(abs_path)) { }
	//  System.out.println("listPaths on path \"" + abs_path.toString() + 
	//	     "\" failed; the path is not a directory.");
	else {}
	// System.out.println("listPaths on path \"" + abs_path.toString() + 
	//	     "\" failed; the path does not exist.");
	return null;
      }
      

    // convert the strings to Paths
    Path paths[] = new Path[dirlist.length];
    for(int i = 0; i < dirlist.length; ++i) {
      //we don't want . or .. entries
      if (dirlist[i].equals(".") || dirlist[i].equals("..")) continue;
      //System.out.println("Raw enumeration of paths in \"" + abs_path.toString() + "\": \"" +
      //		     dirlist[i] + "\"");

      // convert each listing to an absolute path
      Path raw_path = new Path(dirlist[i]);
      if (raw_path.isAbsolute())
	paths[i] = raw_path;
      else
	paths[i] = new Path(abs_path, raw_path);
    }
    return paths;     
  }

  public FSDataOutputStream create(Path f,
				   FsPermission permission,
				   EnumSet<CreateFlag> flag,
				   int bufferSize,
				   short replication,
				   long blockSize,
				   Progressable progress
				   ) throws IOException {
	

    Path abs_path = makeAbsolute(f);
      
    // We ignore progress reporting and replication.
    // Required semantics: if the file exists, overwrite if overwrite == true, and
    // throw an exception if overwrite == false.

    // Step 1: existence test
    if(isDirectory(abs_path))
      throw new IOException("create: Cannot overwrite existing directory \""
			    + abs_path.toString() + "\" with a file");      
    if (!flag.contains(CreateFlag.OVERWRITE)) {
      if (exists(abs_path)) {
	throw new IOException("createRaw: Cannot open existing file \"" 
			      + abs_path.toString() 
			      + "\" for writing without overwrite flag");
      }
    }

    // Step 2: create any nonexistent directories in the path
    Path parent =  abs_path.getParent();
    if (parent != null) { // if parent is root, we're done
      if(!exists(parent)) {
	//System.out.println("createRaw: parent directory of path \""  
	//		 + absfilepath.toString() + "\" does not exist. Creating:");
	mkdirs(parent);
      }
    }

    // Step 3: open the file
    int fh = ceph_open_for_overwrite(abs_path.toString(), (int)permission.toShort());
    if (fh < 0) {
      throw new IOException("createRaw: Open for overwrite failed on path \"" + 
			    abs_path.toString() + "\"");
    }
      
    // Step 4: create the stream
    OutputStream cephOStream = new CephOutputStream(getConf(), fh);
    //System.out.println("createRaw: opened absolute path \""  + absfilepath.toString() 
    //		 + "\" for writing with fh " + fh);

    return new FSDataOutputStream(cephOStream);
  }



  // Opens a Ceph file and attaches the file handle to an FSDataInputStream.
    public FSDataInputStream open(Path path, int bufferSize) throws IOException {
    Path abs_path = makeAbsolute(path);

    if(!isFile(abs_path)) {
      if (!exists(abs_path))
	throw new IOException("open:  absolute path \""  + abs_path.toString()
			      + "\" does not exist");
      else
	throw new IOException("open:  absolute path \""  + abs_path.toString()
			      + "\" is not a file");
    }

    int fh = ceph_open_for_read(abs_path.toString());
    if (fh < 0) {
      throw new IOException("open: Failed to open file " + abs_path.toString());
    }
    long size = ceph_getfilesize(abs_path.toString());
    if (size < 0) {
      throw new IOException("Failed to get file size for file " + abs_path.toString() + 
			    " but succeeded in opening file. Something bizarre is going on.");
    }
    FSInputStream cephIStream = new CephInputStream(getConf(), fh, size);
    return new FSDataInputStream(cephIStream);
  }

  @Override
    public boolean rename(Path src, Path dst) throws IOException {
    // TODO: Check corner cases: dst already exists,
    // or path is directory with children

    return ceph_rename(src.toString(), dst.toString());
  }
  
  public boolean delete(Path path, boolean recursive) throws IOException {
    
    Path abs_path = makeAbsolute(path);      
    
    //System.out.println("delete: Deleting path " + abs_path.toString());
    // sanity check
    if (abs_path.toString().equals("/"))
      throw new IOException("Error: deleting the root directory is a Bad Idea.");
    
    // if the path is a file, try to delete it.
    if (isFile(abs_path)) {
      boolean result = ceph_unlink(path.toString());
      /*      if(!result) {
	System.out.println("delete: failed to delete file \"" +
			   abs_path.toString() + "\".");
			   } */
      return result;
    }
    
    /* If the path is a directory, recursively try to delete its contents,
       and then delete the directory. */
    if (!recursive) {
      throw new IOException("Directories must be deleted recursively!");
    }
    //get the entries; listPaths will remove . and .. for us
    Path[] contents = listPaths(path);
    if (contents == null) {
      // System.out.println("delete: Failed to read contents of directory \"" +
      //	     abs_path.toString() + "\" while trying to delete it");
      return false;
    }
    // delete the entries
    Path parent = abs_path.getParent();
    for (Path p : contents) {
      if (!delete(p, true)) {
	// System.out.println("delete: Failed to delete file \"" + 
	//		 p.toString() + "\" while recursively deleting \""
	//		 + abs_path.toString() + "\"" );
	return false;
      }
    }
    //if we've come this far it's a now-empty directory, so delete it!
    boolean result = ceph_rmdir(path.toString());
    if (!result)
      System.out.println("delete: failed to delete \"" + abs_path.toString() + "\"");
    return result;
  }
   

  //@Override
  private long __getLength(Path path) throws IOException {
    Path abs_path = makeAbsolute(path);

    if (!exists(abs_path)) {
      throw new IOException("org.apache.hadoop.fs.ceph.CephFileSystem.__getLength: File or directory " + abs_path.toString() + " does not exist.");
    }	  

    long filesize = ceph_getfilesize(abs_path.toString());
    if (filesize < 0) {
      throw new IOException("org.apache.hadoop.fs.ceph.CephFileSystem.getLength: Size of file or directory " + abs_path.toString() + " could not be retrieved.");
    }	  
    return filesize;
  }

  /**
   * User-defined replication is not supported for Ceph file systems at the moment.
   */

    public short getReplication(Path path) throws IOException {
    return 1;
  }

    public short getDefaultReplication() {
    return 1;
  }

  /**
   * User-defined replication is not supported for Ceph file systems at the moment.
   */
  public boolean setReplicationRaw(Path path, short replication)
    throws IOException {
    return true;
  }

  public long getBlockSize(Path path) throws IOException {
   
    if (!exists(path)) {
      throw new IOException("org.apache.hadoop.fs.ceph.CephFileSystem.getBlockSize: File or directory " + path.toString() + " does not exist.");
    }
    long result = ceph_getblocksize(path.toString());
    if (!isFile(path)) {
      throw new IOException("org.apache.hadoop.fs.ceph.CephFileSystem.getBlockSize: File or directory " + path.toString() + " is not a file.");
    }
    else {
      System.err.println("DEBUG: getBlockSize: alleged file really is a file");
    }
    if (result < 4096) {
      System.err.println("org.apache.hadoop.fs.ceph.CephFileSystem.getBlockSize: " + 
			 "path exists; strange block size of " + result + " defaulting to 8192");
      return 8192;
    }

    
    return result;
    //return DEFAULT_BLOCK_SIZE;
    //  return ceph_getblocksize(path.toString());

  }

  @Override
    public long getDefaultBlockSize() {
    return DEFAULT_BLOCK_SIZE;
    //return getConf().getLong("fs.ceph.block.size", DEFAULT_BLOCK_SIZE);
  }

  /**
   * Return 1x1 'localhost' cell if the file exists. Return null if otherwise.
   */
  public String[][] getFileCacheHints(Path f, long start, long len)
    throws IOException {
    // TODO: Check this is the correct behavior
    if (!exists(f)) {
      return null;
    }
    return new String[][] { { "localhost" } };
  }

  public void lock(Path path, boolean shared) throws IOException {
    // TODO: Design and implement? or just ignore locking?
    return;
  }

  public void release(Path path) throws IOException {
    return; //deprecated
  }

  /* old API
     @Override
     public void reportChecksumFailure(Path f, 
     FSDataInputStream in, long inPos, 
     FSDataInputStream sums, long sumsPos) {
     // TODO: What to do here?
     return;
     } */

  @Override
    public void moveFromLocalFile(Path src, Path dst) throws IOException {
    if (!ceph_copyFromLocalFile(src.toString(), dst.toString())) {
      throw new IOException("org.apache.hadoop.fs.ceph.CephFileSystem.moveFromLocalFile: failed moving from local file " + src.toString() + " to Ceph file " + dst.toString());
    }
    //FileUtil.copy(localFs, src, this, dst, true, getConf());
  }

  @Override
    public void copyFromLocalFile(Path src, Path dst) throws IOException {
    // make sure Ceph path exists
    Path abs_src = makeAbsolute(src);
    Path abs_dst = makeAbsolute(dst);

    if (isDirectory(abs_dst))
      throw new IOException("Error in copyFromLocalFile: " +
			    "attempting to open an existing directory as a file");
    Path abs_dst_parent = abs_dst.getParent();

    if (!exists(abs_dst_parent))
      mkdirs(abs_dst_parent);

    if (!ceph_copyFromLocalFile(abs_src.toString(), abs_dst.toString())) {
      throw new IOException("org.apache.hadoop.fs.ceph.CephFileSystem.copyFromLocalFile: failed copying from local file " + abs_src.toString() + " to Ceph file " + abs_dst.toString());
    }
    //FileUtil.copy(localFs, src, this, dst, false, true, getConf());
  }



  public void copyToLocalFile(Path ceph_src, Path local_dst, boolean copyCrc) throws IOException {

    Path abs_ceph_src = makeAbsolute(ceph_src);
      
    //System.out.println("CopyToLocalFile: copying Ceph file \"" + abs_ceph_src.toString() + 
    //		 "\" to local file \"" + local_dst.toString() + "\" using client");
    // make sure the alleged source file exists, and is actually a file, not
    // a directory or a ballpoint pen or something
    if (!isFile(abs_ceph_src)) {
      if (!exists(abs_ceph_src)) {
	throw new IOException("copyToLocalFile:  failed copying Ceph file \"" + 
			      abs_ceph_src.toString() + "\" to local file \"" 
			      + local_dst.toString() + 
			      "\" because the source file does not exist");
      }
      else {
	throw new IOException("copyToLocalFile:  failed copying Ceph file \"" + 
			      abs_ceph_src.toString() + "\" to local file \"" + 
			      local_dst.toString() + 
			      "\" because the Ceph path is not a  file");
      }
    }

    // if the destination's parent directory doesn't exist, create it.
    Path local_dst_parent_dir = local_dst.getParent();
    if(null == local_dst_parent_dir)
      throw new IOException("copyToLocalFile:  failed copying Ceph file \"" + 
			    abs_ceph_src.toString() + "\" to local file \"" + 
			    local_dst.toString() + 
			    "\": destination is root");

    if(!localFs.mkdirs(local_dst_parent_dir))
      throw new IOException("copyToLocalFile:  failed copying Ceph file \"" + 
			    abs_ceph_src.toString() + "\" to local file \"" + 
			    local_dst.toString() + 
			    "\": creating the destination's parent directory failed.");
    else
      {
	if (!ceph_copyToLocalFile(abs_ceph_src.toString(), local_dst.toString())) 
	  {
	    throw new IOException("copyToLocalFile:  failed copying Ceph file \"" + 
				  abs_ceph_src.toString() + "\" to local file \"" 
				  + local_dst.toString() + "\"");
	  }
      }
    //System.out.println("CopyToLocalFile: copied Ceph file \"" + abs_ceph_src.toString() + 
    //		 "\" to local file \"" + local_dst.toString() + "\"");
  }


  @Override
    public Path startLocalOutput(Path fsOutputFile, Path tmpLocalFile)
    throws IOException {
    return tmpLocalFile;
  }

  @Override
    public void completeLocalOutput(Path fsOutputFile, Path tmpLocalFile)
    throws IOException {
    moveFromLocalFile(tmpLocalFile, fsOutputFile);
  }

  // diagnostic methods

  /*  void dump() throws IOException {
      store.dump();
      }

      void purge() throws IOException {
      store.purge();
      }*/

}
