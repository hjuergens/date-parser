module Color
        where

data Color
        = Red
        | Orange
        | Yellow
        | Green
        | Blue
        | Purple
        | White
        | Black
        | Custom Int Int Int -- R G B components
        deriving (Show,Eq)

colorToRGB Orange = (255,128,0)
colorToRGB Yellow = (255,255,0)
colorToRGB Green = (0,255,0)
colorToRGB Blue = (0,0,255)
colorToRGB Purple = (255,0,255)
colorToRGB White = (255,255,255)
colorToRGB Black = (0,0,0)
colorToRGB (Custom r g b) = (r,g,b)

instance Show Color where
        show Red = "Red"
        show Orange = "Orange"
        show Yellow = "Yellow"
        show Green = "Green"
        show Blue = "Blue"
        show Purple = "Purple"
        show White = "White"
        show Black = "Black"
        show (Custom r g b) =
                "Custom " ++ show r ++ " " ++
                show g ++ " " ++ show b
