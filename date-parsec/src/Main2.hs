module Main
        where

main = putStrLn "Hello World"

factorial 1 = 1
factorial n = n * factorial (n-1)
