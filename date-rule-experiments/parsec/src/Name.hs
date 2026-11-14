module Main
        where

import System.IO

main = do
        hSetBuffering stdin LineBuffering
        putStrLn "Please enter your name: "
        name <- getLine
        putStrLn ("Hello, " ++ name ++ ", how are you?")