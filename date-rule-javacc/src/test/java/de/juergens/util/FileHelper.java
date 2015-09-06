/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.util;

import javax.swing.text.html.HTMLDocument;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.*;
import java.nio.file.*;
import java.util.function.Consumer;

public class FileHelper {

    public static Collection<Object[]> files() throws URISyntaxException, IOException {

        URI uri = FileHelper.class.getResource("/").toURI();
        Path dir = Paths.get(uri);

        List<Path> builder = new LinkedList<>();

        {
            DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
                @Override
                public boolean accept(Path entry) throws IOException {
                    return entry.toString().endsWith("txt");
                }
            };

            Map<String, String> env = new HashMap<>();
            env.put("create", "true");
            try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                for (Path path : zipfs.getRootDirectories()) {
                    for (Path path2 : Files.newDirectoryStream(path, filter)) {
                        builder.add(path2);
                    }
                }
            }
        }

        try (DirectoryStream<Path> pathes = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path path : pathes) {
                builder.add(path);
            }
        }

        {
            FileVisitor<? super Path> visitor = new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile() && file.endsWith("txt"))
                        builder.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    builder.remove(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(dir, visitor);
        }

        return getCollection(builder);
    }

    public static Collection<Object[]> getCollection(final Iterable<Path> iterable) {
        final Enumeration<Object[]> e = new Enumeration<Object[]>() {
            final Iterator<Path> inner = iterable.iterator();

            public boolean hasMoreElements() {
                return inner.hasNext();
            }

            @Override
            public Object[] nextElement() {
                return new Object[]{inner.next()};
            }
        };
        return Collections.list(e);
    }

    public static void main(String [] args) throws Throwable {
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        // locate file system by using the syntax
        // defined in java.net.JarURLConnection
        URI uri = FileHelper.class.getResource("/").toURI();

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
            Path externalTxtFile = Paths.get("/codeSamples/zipfs/SomeTextFile.txt");
            Path pathInZipfile = zipfs.getPath("/SomeTextFile.txt");
            // copy a file into the zip file
            Files.copy( externalTxtFile,pathInZipfile,
                    StandardCopyOption.REPLACE_EXISTING );
        }
    }
/*
static public List<Object[]> files() throws URISyntaxException, IOException {

        URI uri = FileHelper.class.getResource("/").toURI();
        Path dir = Paths.get(uri);
        List<Path> builder = new LinkedList<Path>();
    DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.toString().endsWith("txt");
        }
    };
        for (Path path : Files.newDirectoryStream(dir, filter)) {
            builder.add(path);
        }
            for (Path path : builder) {
                Path relPath = dir.relativize(path);
        } yield Array[Object]( relPath.toFile )
        }

    static public List<Object[]> lines(File file)  {
        val resourceName = "/" + file.toString;
        val stream = getClass.getResourceAsStream(resourceName);
        for {
        line <- linesFromStream(stream).toArray
        } yield Array[Object]( line )
        }

    static private Iterator[Object]  linesFromStream(InputStream inputStream)  {
        implicit val co = scala.io.Codec.UTF8
        val filteredLines = Source.fromInputStream{
        inputStream
        }.getLines().map(_.trim)
        .filterNot(_.startsWith("#"))
        .filterNot(_.isEmpty)
        .filterNot(_.forall(_.isWhitespace))
        .filterNot(_.forall(_ == '\u200B'))
        filteredLines
        }

    static public List<Object[]> filesLines() {
        implicit val co = scala.io.Codec.UTF8
        for{
        file <- files.map(_.apply(0))
        resourceName = "/" + file.toString
        line <- linesFromStream(getClass.getResourceAsStream(resourceName))
        } yield Array[Object]( file, line )
        }
        }
*/
}
